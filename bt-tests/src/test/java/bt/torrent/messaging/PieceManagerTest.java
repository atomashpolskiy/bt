package bt.torrent.messaging;

import bt.data.ChunkDescriptor;
import bt.net.Peer;
import bt.torrent.BaseBitfieldTest;
import bt.torrent.Bitfield;
import bt.torrent.BitfieldBasedStatistics;
import bt.torrent.PieceSelector;
import bt.torrent.RarestFirstSelectionStrategy;
import bt.torrent.RarestFirstSelector;
import bt.torrent.ValidatingSelector;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class PieceManagerTest extends BaseBitfieldTest {

    @Test
    public void testPieceManager_SelectPieces_RarestSelectionStrategy() {
        Verifier verifier3 = new Verifier(),
                 verifier5 = new Verifier();

        ChunkDescriptor chunk3 = mockChunk(chunkSize, new byte[]{0,0,0,0}, verifier3),
                         chunk5 = mockChunk(chunkSize, new byte[]{0,0,0,0}, verifier5);

        ChunkDescriptor[] chunkArray = new ChunkDescriptor[12];
        Arrays.fill(chunkArray, emptyChunk);
        chunkArray[3] = chunk3;
        chunkArray[5] = chunk5;

        List<ChunkDescriptor> chunks = Arrays.asList(chunkArray);
        Bitfield bitfield = new Bitfield(chunks);
        Assignments assignments = new Assignments();
        BitfieldBasedStatistics pieceStatistics = new BitfieldBasedStatistics(bitfield.getPiecesTotal());
        Predicate<Integer> validator = new IncompleteUnassignedPieceValidator(bitfield, assignments);
        PieceSelector selector = new SelectorAdapter(RarestFirstSelectionStrategy.rarest(), pieceStatistics, validator);

        testPieceManager_SelectPieces_Rarest(bitfield, selector, assignments, pieceStatistics, verifier3, verifier5);
    }

    @Test
    public void testPieceManager_SelectPieces_RarestSelector() {
        Verifier verifier3 = new Verifier(),
                 verifier5 = new Verifier();

        ChunkDescriptor chunk3 = mockChunk(chunkSize, new byte[]{0,0,0,0}, verifier3),
                         chunk5 = mockChunk(chunkSize, new byte[]{0,0,0,0}, verifier5);

        ChunkDescriptor[] chunkArray = new ChunkDescriptor[12];
        Arrays.fill(chunkArray, emptyChunk);
        chunkArray[3] = chunk3;
        chunkArray[5] = chunk5;

        List<ChunkDescriptor> chunks = Arrays.asList(chunkArray);
        Bitfield bitfield = new Bitfield(chunks);
        Assignments assignments = new Assignments();
        BitfieldBasedStatistics pieceStatistics = new BitfieldBasedStatistics(bitfield.getPiecesTotal());
        Predicate<Integer> validator = new IncompleteUnassignedPieceValidator(bitfield, assignments);
        PieceSelector selector = new ValidatingSelector(validator, new RarestFirstSelector(pieceStatistics, false));

        testPieceManager_SelectPieces_Rarest(bitfield, selector, assignments, pieceStatistics, verifier3, verifier5);
    }

    private void testPieceManager_SelectPieces_Rarest(Bitfield bitfield,
                                                      PieceSelector selector,
                                                      Assignments assignments,
                                                      BitfieldBasedStatistics pieceStatistics,
                                                      Verifier verifier3,
                                                      Verifier verifier5) {
        PieceManager pieceManager = new PieceManager(bitfield, selector, assignments, pieceStatistics);
        // peer has piece #3
        Peer peer1 = mock(Peer.class);
        assertFalse(pieceManager.mightSelectPieceForPeer(peer1));
        pieceManager.peerHasBitfield(peer1, new Bitfield(new byte[]{0b1 << (7 - 3), 0}, bitfield.getPiecesTotal()));
        assertTrue(pieceManager.mightSelectPieceForPeer(peer1));
        assertPresentAndHasValue(3, pieceManager.selectPieceForPeer(peer1));

        // another peer has pieces #3 and #5
        Peer peer2 = mock(Peer.class);
        pieceManager.peerHasBitfield(peer2, new Bitfield(new byte[]{(0b1 << (7 - 3)) + (0b1 << (7 - 5)), 0}, bitfield.getPiecesTotal()));
        assertPresentAndHasValue(5, pieceManager.selectPieceForPeer(peer2));

        verifier5.setVerified();
        assertTrue(pieceManager.checkPieceVerified(5));
        assertPresentAndHasValue(3, pieceManager.selectPieceForPeer(peer1));
        assertFalse(pieceManager.mightSelectPieceForPeer(peer2));

        // yet another peer has pieces #7 and #11
        Peer peer3 = mock(Peer.class);
        pieceManager.peerHasBitfield(peer3, new Bitfield(new byte[]{0, 0b1 << (7 - 3)}, bitfield.getPiecesTotal()));
        pieceManager.peerHasPiece(peer3, 7);
        assertPresentAndHasValue(3, pieceManager.selectPieceForPeer(peer1));
        assertFalse(pieceManager.mightSelectPieceForPeer(peer2));
        assertPresentAndHasValue(7, pieceManager.selectPieceForPeer(peer3));

        // peer1 resets connection
        // TODO: due to recent message reorg this part was changed
        // need to refactor piece manager and assignments as well
        pieceManager.unselectPieceForPeer(peer1, 3);
//        assertFalse(pieceManager.mightSelectPieceForPeer(peer1));
        assertTrue(pieceManager.mightSelectPieceForPeer(peer2));
        assertPresentAndHasValue(3, pieceManager.selectPieceForPeer(peer2));

        verifier3.setVerified();
        assertFalse(pieceManager.mightSelectPieceForPeer(peer2));
    }

    private static <T> void assertPresentAndHasValue(T expected, Optional<T> actualOptional) {
        assertTrue("missing value", actualOptional.isPresent());
        assertEquals(expected, actualOptional.get());
    }

    private static class Verifier implements Supplier<Boolean> {

        private boolean verified;

        void setVerified() {
            verified = true;
        }

        @Override
        public Boolean get() {
            return verified;
        }
    }
}
