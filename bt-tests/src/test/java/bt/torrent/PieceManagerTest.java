package bt.torrent;

import bt.data.DataStatus;
import bt.data.IChunkDescriptor;
import bt.metainfo.TorrentId;
import bt.net.IPeerConnection;
import bt.net.Peer;
import bt.protocol.Message;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static bt.TestUtil.assertExceptionWithMessage;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PieceManagerTest {

    static long blockSize = 4;
    static long chunkSize = blockSize * 4;

    static IChunkDescriptor emptyChunk, completeChunk;

    @BeforeClass
    public static void setUp() {
        emptyChunk = mockChunk(blockSize, chunkSize, new byte[]{0,0,0,0}, null);
        completeChunk = mockChunk(blockSize, chunkSize, new byte[]{1,1,1,1}, null);
    }

    @Test
    public void testPieceManager_Bitfield_AllEmpty() {

        IChunkDescriptor[] chunkArray = new IChunkDescriptor[12];
        Arrays.fill(chunkArray, emptyChunk);

        List<IChunkDescriptor> chunks = Arrays.asList(chunkArray);

        IPieceManager pieceManager = new PieceManager(RarestFirstSelector.selector(), chunks);
        assertArrayEquals(new byte[]{0,0}, pieceManager.getBitfield());
        assertFalse(pieceManager.haveAnyData());
        assertEquals(12, pieceManager.getPiecesRemaining());
    }

    @Test
    public void testPieceManager_Bitfield() {

        List<IChunkDescriptor> chunks = Arrays.asList(completeChunk, emptyChunk, emptyChunk, completeChunk,
                                                      emptyChunk, emptyChunk, emptyChunk, completeChunk);

        byte expectedBitfield = (byte) (1 + (0b1 << 4) + (0b1 << 7));

        IPieceManager pieceManager = new PieceManager(RarestFirstSelector.selector(), chunks);
        assertArrayEquals(new byte[]{expectedBitfield}, pieceManager.getBitfield());
        assertTrue(pieceManager.haveAnyData());
        assertEquals(5, pieceManager.getPiecesRemaining());
    }

    @Test
    public void testPieceManager_PeerBitfield_WrongSize() throws Exception {

        IChunkDescriptor[] chunkArray = new IChunkDescriptor[4];
        Arrays.fill(chunkArray, emptyChunk);

        List<IChunkDescriptor> chunks = Arrays.asList(chunkArray);

        IPieceManager pieceManager = new PieceManager(RarestFirstSelector.selector(), chunks);
        Peer peer = mock(Peer.class);
        assertExceptionWithMessage(
                it -> {
                    pieceManager.peerHasBitfield(peer, new byte[]{0,0}); return null;},
                "bitfield has wrong size: 2");
    }

    @Test
    public void testPieceManager_SelectPieces() {

        Verifier verifier3 = new Verifier(),
                 verifier5 = new Verifier();

        IChunkDescriptor chunk3 = mockChunk(blockSize, chunkSize, new byte[]{0,0,0,0}, verifier3),
                         chunk5 = mockChunk(blockSize, chunkSize, new byte[]{0,0,0,0}, verifier5);

        IChunkDescriptor[] chunkArray = new IChunkDescriptor[12];
        Arrays.fill(chunkArray, emptyChunk);
        chunkArray[3] = chunk3;
        chunkArray[5] = chunk5;

        List<IChunkDescriptor> chunks = Arrays.asList(chunkArray);
        IPieceManager pieceManager = new PieceManager(RarestFirstSelector.selector(), chunks);

        // peer has piece #3
        Peer peer1 = mock(Peer.class);
        assertFalse(pieceManager.mightSelectPieceForPeer(peer1));
        pieceManager.peerHasBitfield(peer1, new byte[]{0b1 << (7 - 3), 0});
        assertTrue(pieceManager.mightSelectPieceForPeer(peer1));
        assertHasPiece(3, pieceManager.selectPieceForPeer(peer1));

        // another peer has pieces #3 and #5
        Peer peer2 = mock(Peer.class);
        pieceManager.peerHasBitfield(peer2, new byte[]{(0b1 << (7 - 3)) + (0b1 << (7 - 5)), 0});
        assertHasPiece(5, pieceManager.selectPieceForPeer(peer2));

        verifier5.setVerified();
        assertTrue(pieceManager.checkPieceVerified(5));
        assertHasPiece(3, pieceManager.selectPieceForPeer(peer1));
        assertFalse(pieceManager.mightSelectPieceForPeer(peer2));

        // yet another peer has pieces #7 and #11
        Peer peer3 = mock(Peer.class);
        pieceManager.peerHasBitfield(peer3, new byte[]{0, 0b1 << (7 - 3)});
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

    private static IChunkDescriptor mockChunk(long blockSize, long chunkSize, byte[] bitfield,
                                              Supplier<Boolean> verifier) {

        byte[] _bitfield = Arrays.copyOf(bitfield, bitfield.length);

        IChunkDescriptor chunk = mock(IChunkDescriptor.class);
        when(chunk.getSize()).thenReturn(chunkSize);
        when(chunk.getBlockSize()).thenReturn(blockSize);
        when(chunk.getBitfield()).thenReturn(_bitfield);
        when(chunk.getStatus()).then(it ->
                verifier == null? statusForBitfield(_bitfield)
                        : (verifier.get()? DataStatus.VERIFIED : statusForBitfield(_bitfield)));

        when(chunk.verify()).then(it -> verifier == null? false : verifier.get());

        return chunk;
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

    private static DataStatus statusForBitfield(byte[] bitfield) {

        if (bitfield.length == 0) {
            throw new RuntimeException("Empty bitfield");
        }

        byte first = bitfield[0];
        for (byte b : bitfield) {
            if (b != first) {
                return DataStatus.INCOMPLETE;
            }
        }
        return first == 0? DataStatus.EMPTY : DataStatus.VERIFIED;
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
