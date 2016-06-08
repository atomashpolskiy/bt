package bt.torrent;

import bt.BtException;
import bt.data.DataStatus;
import bt.data.IChunkDescriptor;
import bt.net.PeerConnection;
import bt.protocol.InvalidMessageException;
import bt.protocol.Request;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;

public class PieceManager {

    private List<IChunkDescriptor> chunks;

    /**
     * Indicates if there is at least one verified chunk in the local torrent files.
     */
    private volatile boolean haveAnyData;
    private byte[] bitfield;

    /**
     * Each element of this aggregate bitfield corresponds to
     * an element from the original bitfield with the same index
     * and therefore stores information about exactly 8 torrent pieces.
     * But, in contrast to the standard bitfield that has 1 bit for each piece
     * indicating whether a peer has this piece, the "aggregate" bitfield
     * stores a number (an octet, or 8 bits -- an unsigned byte) for each piece that indicates
     * the total number of peers that have this piece.
     *
     * Used in the piece selection algorithm.
     */
    private long[] aggregateBitfield;

    private Map<PeerConnection, byte[]> peerBitfields;

    public PieceManager(List<IChunkDescriptor> chunks) {
        this.chunks = chunks;

        bitfield = createBitfield(chunks);
        for (byte b : bitfield) {
            if (b != 0) {
                haveAnyData = true;
                break;
            }
        }
        peerBitfields = new HashMap<>();
        aggregateBitfield = new long[bitfield.length];
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

    boolean haveAnyData() {
        return haveAnyData;
    }

    byte[] getBitfield() {
        return Arrays.copyOf(bitfield, bitfield.length);
    }

    // TODO: Check if peer has everything (i.e. is a seeder) and store him separately
    // this should help to improve performance of the next piece selection algorithm
    public void peerHasBitfield(PeerConnection peer, byte[] peerBitfield) {
        if (peerBitfield.length == bitfield.length) {
            byte[] bs = Arrays.copyOf(peerBitfield, peerBitfield.length);
            peerBitfields.put(peer, bs);
            for (int i = 0; i < bs.length; i++) {
                // update combined
                aggregateBitfield[i] += getLongBitmask(bs[i]);
            }
        } else {
            throw new BtException("bitfield has wrong size: " + peerBitfield.length);
        }
    }

    void peerHasPiece(PeerConnection peer, int pieceIndex) {
        byte[] peerBitfield = peerBitfields.get(peer);
        if (peerBitfield == null) {
            peerBitfield = new byte[bitfield.length];
            peerBitfields.put(peer, peerBitfield);
        }
        setBit(peerBitfield, pieceIndex);
    }

    boolean checkPieceCompleted(int pieceIndex) {
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
            e.printStackTrace();
        }
        return false;
    }

    // TODO: cache the results until something is changed ?

    /**
     * Returns up to {@code limit} pieces that are recommended to download first.
     * @param limit Max amount of pieces to return, exclusive
     */
    Map<Integer, List<PeerConnection>> getNextPieces(int limit) {

        if (limit <= 0) {
            throw new BtException("Invalid limit: " + limit);
        }

        // update the aggregate bitfield for disconnected peers
        Iterator<Map.Entry<PeerConnection, byte[]>> iter = peerBitfields.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<PeerConnection, byte[]> peerBitfield = iter.next();
            if (peerBitfield.getKey().isClosed()) {
                // subtract peer bitfield from the aggregate bitfield
                for (int i = 0; i < peerBitfield.getValue().length; i++) {
                    aggregateBitfield[i] -= getLongBitmask(peerBitfield.getValue()[i]);
                }
                iter.remove();
            }
        }

        PriorityQueue<Long> rarestPieces = new PriorityQueue<>(new Comparator<Long>() {
            @Override
            public int compare(Long o1, Long o2) {
                if (o1.intValue() > o2.intValue()) {
                    return 1;
                } else if (o1.intValue() < o2.intValue()) {
                    return -1;
                } else {
                    return 0;
                }
            }
        });

        outer:
        for (int i = 0; i < aggregateBitfield.length; i++) {

            long bitmask = aggregateBitfield[i];
            for (int j = 0; j < 8; j++) {
                // unpack individual pieces count from bitmask
                int pieceIndex = i * 8 + j;
                if (pieceIndex >= chunks.size()) {
                    break outer; // no more pieces left
                } else if (checkPieceCompleted(pieceIndex)) {
                    // already have this piece
                    continue;
                }
                // get (j+1)-th octet (from high to low) and normalize to an unsigned byte value
                int shift = ((7 - j) * 8);
                int count = (int) ((bitmask & (0xFFL << shift)) >> shift) & 0xFF; // it's an octet so cast is safe
                if (count > 0) {
                    // storing two ints in a long value: <piece-index><count>
                    long packed = (((long)pieceIndex) << 32) + count;
                    rarestPieces.add(packed);
                }
            }
        }

        Map<Integer, List<PeerConnection>> peersWithPieces = new HashMap<>();
        Long rarestPiece;
        int collected = 0, k = limit * 3;
        while ((rarestPiece = rarestPieces.poll()) != null && collected < k) {
            int pieceIndex = (int) (rarestPiece >> 32); // retrieving piece-index from packed value
            for (Map.Entry<PeerConnection, byte[]> peerBitfield : peerBitfields.entrySet()) {
               if (getBit(peerBitfield.getValue(), pieceIndex) != 0) {
                   List<PeerConnection> peersWithPiece = peersWithPieces.get(pieceIndex);
                   if (peersWithPiece == null) {
                       // increment count of collected pieces
                       collected++;
                       peersWithPiece = new ArrayList<>();
                       peersWithPieces.put(pieceIndex, peersWithPiece);
                   }
                   peersWithPiece.add(peerBitfield.getKey());
               }
           }
        }

        // randomly choose min(limit, collected) rarest pieces
        Map<Integer, List<PeerConnection>> result = new HashMap<>();
        @SuppressWarnings("unchecked")
        Map.Entry<Integer, List<PeerConnection>>[] entries =
                peersWithPieces.entrySet().toArray(new Map.Entry[peersWithPieces.size()]);
        Random random = new Random(System.currentTimeMillis());
        int actualLimit = Math.min(limit, peersWithPieces.size());
        Map.Entry<Integer, List<PeerConnection>> entry;
        for (int i = 0; i < actualLimit; i++) {
            do {
                entry = entries[random.nextInt(entries.length)];
            } while (result.containsKey(entry.getKey()));
            result.put(entry.getKey(), entry.getValue());
        }

        return result;
    }

    List<Request> buildRequestsForPiece(int pieceIndex) {

        if (pieceIndex >= chunks.size()) {
            throw new BtException("piece index is too large: " + pieceIndex);
        }

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

    /**
     * Transforms a traditional byte bitmask into a long bitmask.
     * Each n-th bit (starting with n = 0) from the original bitmask is set into the
     * (n * 8)-th bit of the new bitmask.
     */
    private static long getLongBitmask(byte bitmask) {

        long lb = 0;
        for (int i = 0; i < 8; i++) {
            // checking bits from high to low
            if ((bitmask & (0b1 << (7 - i))) != 0) {
                lb += 1L << ((7 - i) * 8);
            }
        }
        return lb;
    }
}
