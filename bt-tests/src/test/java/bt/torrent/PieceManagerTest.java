package bt.torrent;

import bt.data.IChunkDescriptor;
import bt.metainfo.TorrentId;
import bt.net.IPeerConnection;
import bt.net.Peer;
import bt.protocol.Message;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static bt.TestUtil.assertExceptionWithMessage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class PieceManagerTest extends BaseBitfieldTest {

    @Test
    public void testPieceManager_SelectPieces() {

        final int PIECES_TOTAL = 12;

        Verifier verifier3 = new Verifier(),
                 verifier5 = new Verifier();

        IChunkDescriptor chunk3 = mockChunk(chunkSize, new byte[]{0,0,0,0}, verifier3),
                         chunk5 = mockChunk(chunkSize, new byte[]{0,0,0,0}, verifier5);

        IChunkDescriptor[] chunkArray = new IChunkDescriptor[12];
        Arrays.fill(chunkArray, emptyChunk);
        chunkArray[3] = chunk3;
        chunkArray[5] = chunk5;

        List<IChunkDescriptor> chunks = Arrays.asList(chunkArray);
        Bitfield bitfield = new Bitfield(chunks);
        IPieceManager pieceManager = new PieceManager(bitfield, RarestFirstSelectionStrategy.regular());

        // peer has piece #3
        Peer peer1 = mock(Peer.class);
        assertFalse(pieceManager.mightSelectPieceForPeer(peer1));
        pieceManager.peerHasBitfield(peer1, new Bitfield(new byte[]{0b1 << (7 - 3), 0}, PIECES_TOTAL));
        assertTrue(pieceManager.mightSelectPieceForPeer(peer1));
        assertHasPiece(3, pieceManager.selectPieceForPeer(peer1));

        // another peer has pieces #3 and #5
        Peer peer2 = mock(Peer.class);
        pieceManager.peerHasBitfield(peer2, new Bitfield(new byte[]{(0b1 << (7 - 3)) + (0b1 << (7 - 5)), 0}, PIECES_TOTAL));
        assertHasPiece(5, pieceManager.selectPieceForPeer(peer2));

        verifier5.setVerified();
        assertTrue(pieceManager.checkPieceVerified(5));
        assertHasPiece(3, pieceManager.selectPieceForPeer(peer1));
        assertFalse(pieceManager.mightSelectPieceForPeer(peer2));

        // yet another peer has pieces #7 and #11
        Peer peer3 = mock(Peer.class);
        pieceManager.peerHasBitfield(peer3, new Bitfield(new byte[]{0, 0b1 << (7 - 3)}, PIECES_TOTAL));
        pieceManager.peerHasPiece(peer3, 7);
        assertHasPiece(3, pieceManager.selectPieceForPeer(peer1));
        assertFalse(pieceManager.mightSelectPieceForPeer(peer2));
        assertHasPiece(7, pieceManager.selectPieceForPeer(peer3));

        // peer1 resets connection
        // TODO: due to recent message reorg this part was changed
        // need to refactor piece manager and assignments as well
        pieceManager.unselectPieceForPeer(peer1, 3);
//        assertFalse(pieceManager.mightSelectPieceForPeer(peer1));
        assertTrue(pieceManager.mightSelectPieceForPeer(peer2));
        assertHasPiece(3, pieceManager.selectPieceForPeer(peer2));

        verifier3.setVerified();
        assertFalse(pieceManager.mightSelectPieceForPeer(peer2));
    }

    @Test
    public void testPieceManager_PeerBitfield_WrongSize() throws Exception {

        IChunkDescriptor[] chunkArray = new IChunkDescriptor[4];
        Arrays.fill(chunkArray, emptyChunk);

        List<IChunkDescriptor> chunks = Arrays.asList(chunkArray);
        Bitfield bitfield = new Bitfield(chunks);

        IPieceManager pieceManager = new PieceManager(bitfield, RarestFirstSelectionStrategy.regular());
        Peer peer = mock(Peer.class);
        assertExceptionWithMessage(
                it -> {
                    pieceManager.peerHasBitfield(peer, new Bitfield(16)); return null;},
                "bitfield has wrong size: 16. Expected: 4");
    }

    private static IPeerConnection mockPeer(TorrentId torrentId) {

        return new IPeerConnection() {

            private boolean closed;
            private Peer peer = mock(Peer.class);

            @Override
            public TorrentId getTorrentId() {
                return torrentId;
            }

            @Override
            public Message readMessageNow() {
                return null;
            }

            @Override
            public Message readMessage(long timeout) {
                return null;
            }

            @Override
            public void postMessage(Message message) {

            }

            @Override
            public Peer getRemotePeer() {
                return peer;
            }

            @Override
            public void closeQuietly() {
                closed = true;
            }

            @Override
            public boolean isClosed() {
                return closed;
            }

            @Override
            public long getLastActive() {
                return 0;
            }

            @Override
            public void close() throws IOException {
                closed = true;
            }
        };
    }

    private static void assertHasPiece(Integer expectedIndex, Optional<Integer> actualIndex) {
        assertTrue("missing index", actualIndex.isPresent());
        assertEquals(expectedIndex, actualIndex.get());
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
