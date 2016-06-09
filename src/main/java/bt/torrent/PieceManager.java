package bt.torrent;

import bt.BtException;
import bt.data.DataStatus;
import bt.data.IChunkDescriptor;
import bt.net.PeerConnection;
import bt.protocol.InvalidMessageException;
import bt.protocol.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class PieceManager implements IPieceManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(PieceManager.class);

    private List<IChunkDescriptor> chunks;

    /**
     * Indicates if there is at least one verified chunk in the local torrent files.
     */
    private volatile boolean haveAnyData;
    private byte[] bitfield;

    private Map<PeerConnection, byte[]> peerBitfields;

    private PieceSelector selector;
    private PieceStats pieceStats;

    public PieceManager(PieceSelector selector, List<IChunkDescriptor> chunks) {

        this.selector = selector;
        this.chunks = chunks;


        bitfield = createBitfield(chunks);

        for (byte b : bitfield) {
            if (b != 0) {
                haveAnyData = true;
                break;
            }
        }

        peerBitfields = new HashMap<>();

        pieceStats = new PieceStats(chunks.size());
    }

    /**
     * Creates a standard bittorrent bitfield, where n-th bit
     * (couting from high position to low) indicates the availability of n-th piece.
     */
    private byte[] createBitfield(List<IChunkDescriptor> chunks) {

        int chunkCount = chunks.size();
        byte[] bitfield = new byte[(int) Math.ceil(chunkCount / 8d)];
        int bitfieldIndex = 0;
        while (chunkCount > 0) {
            int b = 0, offset = bitfieldIndex * 8;
            int k = chunkCount < 8? chunkCount : 8;
            for (int i = 0; i < k; i++) {
                IChunkDescriptor chunk = chunks.get(offset + i);
                if (chunk.getStatus() == DataStatus.VERIFIED) {
                    b += 0b1 << (7 - i);
                }
            }
            bitfield[bitfieldIndex] = (byte) b;
            bitfieldIndex++;
            chunkCount -= 8;
        }
        return bitfield;
    }

    @Override
    public boolean haveAnyData() {
        return haveAnyData;
    }

    @Override
    public byte[] getBitfield() {
        return Arrays.copyOf(bitfield, bitfield.length);
    }

    // TODO: Check if peer has everything (i.e. is a seeder) and store him separately
    // this should help to improve performance of the next piece selection algorithm
    @Override
    public void peerHasBitfield(PeerConnection peer, byte[] peerBitfield) {

        if (peerBitfield.length == bitfield.length) {
            byte[] bs = Arrays.copyOf(peerBitfield, peerBitfield.length);
            peerBitfields.put(peer, bs);
            pieceStats.addBitfield(peerBitfield);
        } else {
            throw new BtException("bitfield has wrong size: " + peerBitfield.length);
        }
    }

    @Override
    public void peerHasPiece(PeerConnection peer, int pieceIndex) {

        validatePieceIndex(pieceIndex);

        byte[] peerBitfield = peerBitfields.get(peer);
        if (peerBitfield == null) {
            peerBitfield = new byte[bitfield.length];
            peerBitfields.put(peer, peerBitfield);
        }

        setBit(peerBitfield, pieceIndex);
        pieceStats.addPiece(pieceIndex);
    }

    @Override
    public boolean checkPieceCompleted(int pieceIndex) {
        try {
            if (getBit(bitfield, pieceIndex) == 1) {
                return true;
            }

            IChunkDescriptor chunk = chunks.get(pieceIndex);
            if (chunk.verify()) {
                setBit(bitfield, pieceIndex);
                haveAnyData = true;
                return true;
            }
        } catch (Exception e) {
            LOGGER.error("Failed to verify chunk {piece index: " + pieceIndex + "}", e);
        }
        return false;
    }

    // TODO: cache the results until something is changed ?

    /**
     * Returns up to {@code limit} pieces that are recommended to download first.
     * @param limit Max amount of pieces to return, exclusive
     */
    @Override
    public Map<Integer, List<PeerConnection>> getNextPieces(int limit) {

        if (limit <= 0) {
            throw new BtException("Invalid limit: " + limit);
        }

        // update the aggregate bitfield for disconnected peers
        Iterator<Map.Entry<PeerConnection, byte[]>> iter = peerBitfields.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<PeerConnection, byte[]> peerBitfield = iter.next();
            if (peerBitfield.getKey().isClosed()) {
                pieceStats.removeBitfield(peerBitfield.getValue());
                iter.remove();
            }
        }

        int chunkCount = chunks.size();
        Integer[] pieces = selector.getNextPieces(
                pieceStats, limit, i -> (i < chunkCount) && !checkPieceCompleted(i));

        Map<Integer, List<PeerConnection>> result = new HashMap<>();
        for (Integer piece : pieces) {
            for (Map.Entry<PeerConnection, byte[]> peerBitfield : peerBitfields.entrySet()) {
                if (getBit(peerBitfield.getValue(), piece) != 0) {
                    List<PeerConnection> peersWithPiece = result.get(piece);
                    if (peersWithPiece == null) {
                        peersWithPiece = new ArrayList<>();
                        result.put(piece, peersWithPiece);
                    }
                    peersWithPiece.add(peerBitfield.getKey());
                }
            }
        }
        return result;
    }

    @Override
    public List<Request> buildRequestsForPiece(int pieceIndex) {

        validatePieceIndex(pieceIndex);

        List<Request> requests = new ArrayList<>();

        IChunkDescriptor chunk = chunks.get(pieceIndex);
        byte[] bitfield = chunk.getBitfield();
        long blockSize = chunk.getBlockSize(),
             chunkSize = chunk.getSize();

        for (int i = 0; i < bitfield.length; i++) {
            if (bitfield[i] == 0) {
                int offset = (int) (i * blockSize);
                int length = (int) Math.min(blockSize, chunkSize - offset);
                try {
                    requests.add(new Request(pieceIndex, offset, length));
                } catch (InvalidMessageException e) {
                    // shouldn't happen
                    throw new BtException("Unexpected error", e);
                }
            }
        }
        return requests;
    }

    private void validatePieceIndex(int pieceIndex) {
        if (pieceIndex < 0 || pieceIndex >= chunks.size()) {
            throw new BtException("Illegal piece index: " + pieceIndex);
        }
    }

    /**
     * Sets n-th bit in the bitfield
     * (which is considered a continuous bit array of bits, indexed starting with 0 from left to right)
     */
    private static void setBit(byte[] bitfield, int bitAbsIndex) {

        int byteIndex = (int) Math.floor(bitAbsIndex / 8d);
        if (byteIndex >= bitfield.length) {
            throw new BtException("bit index is too large: " + bitAbsIndex);
        }

        int bitIndex = bitAbsIndex % 8;
        int shift = (7 - bitIndex);
        int bitMask = 0b1 << shift;
        byte currentByte = bitfield[byteIndex];
        bitfield[byteIndex] = (byte) (currentByte | bitMask);
    }

    /**
     * Gets n-th bit in the bitfield
     * (which is considered a continuous bit array of bits, indexed starting with 0 from left to right)
     */
    private static int getBit(byte[] bitfield, int bitAbsIndex) {

        int byteIndex = (int) Math.floor(bitAbsIndex / 8d);
        if (byteIndex >= bitfield.length) {
            throw new BtException("bit index is too large: " + bitAbsIndex);
        }

        int bitIndex = bitAbsIndex % 8;
        int shift = (7 - bitIndex);
        int bitMask = 0b1 << shift ;
        return (bitfield[byteIndex] & bitMask) >> shift;
    }

    private class PieceStats implements IPieceStats {

        private int[] pieceTotals;

        PieceStats(int pieceCount) {
            this.pieceTotals = new int[pieceCount];
        }

        void addBitfield(byte[] bitfield) {
            for (int i = 0; i < pieceTotals.length; i++) {
                pieceTotals[i] += getBit(bitfield, i);
            }
        }

        void removeBitfield(byte[] bitfield) {
            for (int i = 0; i < pieceTotals.length; i++) {
                pieceTotals[i] -= getBit(bitfield, i);
            }
        }

        void addPiece(int pieceIndex) {
            pieceTotals[pieceIndex]++;
        }

        @Override
        public int getCount(int pieceIndex) {
            return pieceTotals[pieceIndex];
        }

        @Override
        public int size() {
            return pieceTotals.length;
        }
    }
}
