package bt.torrent.messaging;

import bt.net.Peer;
import bt.runtime.Config;
import bt.torrent.Bitfield;
import bt.torrent.BitfieldBasedStatistics;
import bt.torrent.selector.PieceSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

class Assignments {

    private static final Logger LOGGER = LoggerFactory.getLogger(Assignments.class);

    private static final int MAX_ASSIGNED_PIECES_PER_PEER = 50;

    private Config config;

    private Bitfield bitfield;
    private PieceSelector selector;
    private BitfieldBasedStatistics pieceStatistics;

    private Set<Integer> assignedPieces;
    private Map<Peer, Assignment> assignments;
    private Map<Peer, LinkedList<Integer>> interestingPeers;

    private Random random;

    Assignments(Bitfield bitfield, PieceSelector selector, BitfieldBasedStatistics pieceStatistics, Config config) {
        this.bitfield = bitfield;
        this.selector = selector;
        this.pieceStatistics = pieceStatistics;
        this.config = config;

        this.assignedPieces = new HashSet<>();
        this.assignments = new HashMap<>();
        this.interestingPeers = new HashMap<>();

        this.random = new Random(System.currentTimeMillis());
    }

    public Assignment get(Peer peer) {
        return assignments.get(peer);
    }

    public void remove(Assignment assignment) {
        assignment.abort();
        assignments.remove(assignment.getPeer());
        assignedPieces.remove(assignment.getPiece());
    }

    public int count() {
        return assignments.size();
    }

    public Optional<Assignment> assign(Peer peer) {
        LinkedList<Integer> pieces = interestingPeers.get(peer);
        if (pieces == null || pieces.isEmpty()) {
            return Optional.empty();
        }

        boolean endgame = isEndgame();

        StringBuilder buf = LOGGER.isTraceEnabled() ? new StringBuilder() : null;
        if (LOGGER.isTraceEnabled()) {
            buf.append("Trying to claim next assignment for peer ");
            buf.append(peer);
            buf.append(". Number of remaining pieces: ");
            buf.append(bitfield.getPiecesRemaining());
            buf.append(", number of pieces in progress: ");
            buf.append(assignments.size());
            buf.append(", endgame: " + endgame);
            buf.append(". ");
        }

        Optional<Integer> selectedPiece;
        if (endgame) {
            // take random piece to minimize number of pieces
            // requested from different peers at the same time
            Integer pieceIndex = pieces.remove(random.nextInt(pieces.size()));
            selectedPiece = Optional.of(pieceIndex);
        } else {

            Integer piece;
            boolean assigned = true;
            Iterator<Integer> iter = pieces.iterator();
            do {
                piece = iter.next();
                if (bitfield.isComplete(piece)) {
                    iter.remove();
                    if (LOGGER.isTraceEnabled()) {
                        buf.append("Checking next piece in queue: {" + piece + "}; piece is completed. ");
                    }
                    continue;
                }
                assigned = assignedPieces.contains(piece);
                if (assigned && LOGGER.isTraceEnabled()) {
                    buf.append("Checking next piece in queue: {" + piece + "}; piece is assigned. ");
                }
            } while (assigned && iter.hasNext());

            if (!assigned) {
                iter.remove();
            }
            selectedPiece = assigned ? Optional.empty() : Optional.of(piece);
        }

        if (LOGGER.isTraceEnabled()) {
            if (selectedPiece.isPresent()) {
                buf.append(" => Assigning piece #");
                buf.append(selectedPiece.get());
                buf.append(" to current peer");
            } else {
                buf.append(" => No pieces to assign.");
            }
            LOGGER.trace(buf.toString());
        }

        return selectedPiece.isPresent() ? Optional.of(assign(peer, selectedPiece.get())) : Optional.empty();
    }

    private boolean isEndgame() {
        // if all remaining pieces are requested,
        // that would mean that we have entered the "endgame" mode
        return bitfield.getPiecesRemaining() <= assignedPieces.size();
    }

    private Assignment assign(Peer peer, Integer piece) {
        Assignment assignment = new Assignment(peer, piece, config.getMaxPieceReceivingTime());
        assignments.put(peer, assignment);
        assignedPieces.add(piece);
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Assigning piece #{} to peer: {}", piece, peer);
        }
        return assignment;
    }

    /**
     * Updates the lists of interesting pieces for the provided peers.
     *
     * @return Collection of peers that have interesting pieces and can be given an assignment
     */
    public Set<Peer> update(Set<Peer> peers) {
        interestingPeers.clear();

        Iterator<Integer> suggested = selector.getNextPieces(pieceStatistics).iterator();
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Updating assignments. Piece selector has more pieces: {}, number of ready peers: {}, number of assigned peers: {}",
                    suggested.hasNext(), peers.size(), assignments.size());
        }
        while (suggested.hasNext() && peers.size() > 0) {
            Integer piece = suggested.next();
            Iterator<Peer> iter = peers.iterator();
            while (iter.hasNext()) {
                Peer peer = iter.next();
                Optional<Bitfield> peerBitfield = pieceStatistics.getPeerBitfield(peer);
                if (!peerBitfield.isPresent()) {
                    LOGGER.warn("Bitfield is not present, skipping... peer: {}", peer);
                    iter.remove();
                    continue;
                }
                boolean hasPiece = peerBitfield.get().isVerified(piece);
                LinkedList<Integer> queue = interestingPeers.get(peer);
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Suggested piece #{}. Checking peer {}. Peer has piece: {}, number of pieces in peer's queue: {}",
                            piece, peer, hasPiece, (queue == null ? 0 : queue.size()));
                }
                if (hasPiece) {
                    if (queue == null) {
                        queue = new LinkedList<>();
                        interestingPeers.put(peer, queue);
                    }
                    queue.add(piece);
                    if (queue.size() >= MAX_ASSIGNED_PIECES_PER_PEER) {
                        iter.remove();
                    }
                }
            }
        }

        return interestingPeers.keySet();
    }
}
