package bt.torrent;

import bt.BtException;
import bt.data.DataStatus;
import bt.data.IChunkDescriptor;
import bt.net.IPeerConnection;
import bt.protocol.InvalidMessageException;
import bt.protocol.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public class PieceManager implements IPieceManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(PieceManager.class);

    private List<IChunkDescriptor> chunks;
    private int completePieces;

    /**
     * Indicates if there is at least one verified chunk in the local torrent files.
     */
    private volatile boolean haveAnyData;
    private byte[] bitfield;

    private PieceSelectorHelper pieceSelectorHelper;
    private Assignments assignments;
    private Predicate<Integer> selectionValidator;

    public PieceManager(PieceSelector selector, List<IChunkDescriptor> chunks) {

        this.chunks = chunks;

        bitfield = createBitfield(chunks);
        for (byte b : bitfield) {
            if (b != 0) {
                haveAnyData = true;
                break;
            }
        }

        pieceSelectorHelper = new PieceSelectorHelper(new PieceStats(chunks.size()), selector,
                pieceIndex -> !checkPieceCompleted(pieceIndex), bitfield.length);

        assignments = new Assignments();
        selectionValidator = pieceIndex -> !assignments.getAssignee(pieceIndex).isPresent();
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
                    completePieces++;
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
    public void peerHasBitfield(IPeerConnection peer, byte[] peerBitfield) {

        if (peerBitfield.length == bitfield.length) {
            byte[] bs = Arrays.copyOf(peerBitfield, peerBitfield.length);
            pieceSelectorHelper.addPeerBitfield(peer, bs);
        } else {
            throw new BtException("bitfield has wrong size: " + peerBitfield.length);
        }
    }

    @Override
    public void peerHasPiece(IPeerConnection peer, Integer pieceIndex) {
        validatePieceIndex(pieceIndex);
        pieceSelectorHelper.addPeerPiece(peer, pieceIndex);
    }

    @Override
    public boolean checkPieceCompleted(Integer pieceIndex) {
        return checkPieceCompleted(pieceIndex, false);
    }

    @Override
    public boolean checkPieceVerified(Integer pieceIndex) {
        return checkPieceCompleted(pieceIndex, true);
    }

    private boolean checkPieceCompleted(Integer pieceIndex, boolean verifiedOnly) {
        boolean completed = false;
        try {
            if (getBit(bitfield, pieceIndex) == 1) {
                completed = true;

            } else {
                IChunkDescriptor chunk = chunks.get(pieceIndex);
                switch (chunk.getStatus()) {
                    case VERIFIED: {
                        setBit(bitfield, pieceIndex);
                        haveAnyData = true;
                        completePieces++;
                        completed = true;
                        break;
                    }
                    case COMPLETE: {
                        // chunk is completed but hasn't been verified yet
                        completed = !verifiedOnly;
                        break;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to check if chunk is complete {piece index: " + pieceIndex + "}", e);
        }

        if (completed && assignments.getAssignee(pieceIndex).isPresent()) {
            assignments.removeAssignee(pieceIndex);
        }
        return completed;
    }

    @Override
    public boolean mightSelectPieceForPeer(IPeerConnection peer) {
        Optional<Integer> piece = pieceSelectorHelper.selectPieceForPeer(peer, selectionValidator);
        return piece.isPresent() && !assignments.getAssignee(piece.get()).isPresent();
    }

    @Override
    public Optional<Integer> selectPieceForPeer(IPeerConnection peer) {
        Optional<Integer> assignedPiece = assignments.getAssignedPiece(peer);
        return assignedPiece.isPresent()? assignedPiece : selectAndAssignPiece(peer);
    }

    private Optional<Integer> selectAndAssignPiece(IPeerConnection peer) {

        pieceSelectorHelper.getKnownPeers().stream()
                .filter(IPeerConnection::isClosed).forEach(assignments::removeAssignment);

        Optional<Integer> piece = pieceSelectorHelper.selectPieceForPeer(peer, selectionValidator);
        if (piece.isPresent()) {
            assignments.assignPiece(peer, piece.get());
        }
        return piece;
    }

    @Override
    public List<Request> buildRequestsForPiece(Integer pieceIndex) {

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

    @Override
    public int piecesLeft() {

        int left = chunks.size() - completePieces;
        if (left < 0) {
            // some algorithm malfunction
            throw new BtException("Unexpected number of pieces left: " + left);
        }
        return left;
    }

    private void validatePieceIndex(Integer pieceIndex) {
        if (pieceIndex < 0 || pieceIndex >= chunks.size()) {
            throw new BtException("Illegal piece index: " + pieceIndex);
        }
    }

    /**
     * Sets n-th bit in the bitfield
     * (which is considered a continuous bit array of bits, indexed starting with 0 from left to right)
     */
    private static void setBit(byte[] bitfield, int bitAbsIndex) {

        int byteIndex = (int) (bitAbsIndex / 8d);
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

        int byteIndex = (int) (bitAbsIndex / 8d);
        if (byteIndex >= bitfield.length) {
            throw new BtException("bit index is too large: " + bitAbsIndex);
        }

        int bitIndex = bitAbsIndex % 8;
        int shift = (7 - bitIndex);
        int bitMask = 0b1 << shift ;
        return (bitfield[byteIndex] & bitMask) >> shift;
    }

    private static class PieceSelectorHelper {

        private Map<IPeerConnection, byte[]> peerBitfields;

        private PieceStats stats;
        private PieceSelector selector;
        private Predicate<Integer> validator;

        private int bitfieldLength;

        PieceSelectorHelper(PieceStats stats, PieceSelector selector,
                            Predicate<Integer> validator, int bitfieldLength) {
            this.stats = stats;
            this.selector = selector;
            this.validator = validator;
            peerBitfields = new HashMap<>();

            this.bitfieldLength = bitfieldLength;
        }

        Collection<IPeerConnection> getKnownPeers() {
            return peerBitfields.keySet();
        }

        void addPeerBitfield(IPeerConnection peer, byte[] peerBitfield) {
            peerBitfields.put(peer, peerBitfield);
            stats.addBitfield(peerBitfield);
        }

        void addPeerPiece(IPeerConnection peer, Integer pieceIndex) {
            byte[] peerBitfield = peerBitfields.get(peer);
            if (peerBitfield == null) {
                peerBitfield = new byte[bitfieldLength];
                peerBitfields.put(peer, peerBitfield);
            }

            setBit(peerBitfield, pieceIndex);
            stats.addPiece(pieceIndex);
        }

        Optional<Integer> selectPieceForPeer(IPeerConnection peer, Predicate<Integer> validator) {

            byte[] peerBitfield = peerBitfields.get(peer);
            if (peerBitfield != null) {
                Integer[] pieces = getNextPieces();
                for (Integer piece : pieces) {
                    if (getBit(peerBitfield, piece) != 0 && validator.test(piece)) {
                        return Optional.of(piece);
                    }
                }
            }
            return Optional.empty();
        }

        Integer[] getNextPieces() {

            // update the aggregate bitfield for disconnected peers
            Iterator<Map.Entry<IPeerConnection, byte[]>> iter = peerBitfields.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<IPeerConnection, byte[]> peerBitfield = iter.next();
                if (peerBitfield.getKey().isClosed()) {
                    stats.removeBitfield(peerBitfield.getValue());
                    iter.remove();
                }
            }

            return selector.getNextPieces(stats, peerBitfields.size(),
                    pieceIndex -> (pieceIndex < stats.size()) && validator.test(pieceIndex));
        }
    }

    private static class PieceStats implements IPieceStats {

        private int[] pieceTotals;
        private long changesCount;

        PieceStats(int pieceCount) {
            this.pieceTotals = new int[pieceCount];
        }

        void addBitfield(byte[] bitfield) {
            for (int i = 0; i < pieceTotals.length; i++) {
                if (getBit(bitfield, i) == 1) {
                    pieceTotals[i]++;
                    changesCount++;
                }
            }
        }

        void removeBitfield(byte[] bitfield) {
            for (int i = 0; i < pieceTotals.length; i++) {
                if (getBit(bitfield, i) == 1) {
                    pieceTotals[i]--;
                    changesCount--;
                }
            }
        }

        void addPiece(Integer pieceIndex) {
            pieceTotals[pieceIndex]++;
            changesCount++;
        }

        long getChangesCount() {
            return changesCount;
        }

        void resetChangesCount() {
            changesCount = 0;
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

    private static class Assignments {

        private Map<IPeerConnection, Integer> assignedPieces;
        private Map<Integer, IPeerConnection> assignedPeers;

        Assignments() {
            assignedPieces = new HashMap<>();
            assignedPeers = new HashMap<>();
        }

        void assignPiece(IPeerConnection peer, Integer pieceIndex) {
            refreshAssignments();
            assignedPieces.put(peer, pieceIndex);
            assignedPeers.put(pieceIndex, peer);
        }

        Optional<Integer> getAssignedPiece(IPeerConnection peer) {
            refreshAssignments();
            return Optional.ofNullable(assignedPieces.get(peer));
        }

        Optional<IPeerConnection> getAssignee(Integer pieceIndex) {
            refreshAssignments();
            return Optional.ofNullable(assignedPeers.get(pieceIndex));
        }

        void removeAssignee(Integer pieceIndex) {
            IPeerConnection assignee = assignedPeers.remove(pieceIndex);
            if (assignee != null) {
                assignedPieces.remove(assignee);
            }
        }

        void removeAssignment(IPeerConnection peer) {
            Integer assignedPiece = assignedPieces.remove(peer);
            if (assignedPiece != null) {
                assignedPeers.remove(assignedPiece);
            }
        }

        private void refreshAssignments() {
            Iterator<Map.Entry<IPeerConnection, Integer>> iter = assignedPieces.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<IPeerConnection, Integer> entry = iter.next();
                if (entry.getKey().isClosed()) {
                    assignedPeers.remove(entry.getValue());
                    iter.remove();
                }
            }
        }
    }
}
