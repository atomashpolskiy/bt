package bt.torrent;

import bt.data.DataStatus;
import bt.data.IChunkDescriptor;
import bt.net.PeerConnection;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static bt.TestUtil.assertExceptionWithMessage;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
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

        IPieceManager IPieceManager = new PieceManager(new RarestFirstSelector(false), chunks);
        assertArrayEquals(new byte[]{0,0}, IPieceManager.getBitfield());
    }

    @Test
    public void testPieceManager_Bitfield() {

        List<IChunkDescriptor> chunks = Arrays.asList(completeChunk, emptyChunk, emptyChunk, completeChunk,
                                                      emptyChunk, emptyChunk, emptyChunk, completeChunk);

        byte expectedBitfield = (byte) (1 + (0b1 << 4) + (0b1 << 7));

        IPieceManager IPieceManager = new PieceManager(new RarestFirstSelector(false), chunks);
        assertArrayEquals(new byte[]{expectedBitfield}, IPieceManager.getBitfield());
    }

    @Test
    public void testPieceManager_PeerBitfield_WrongSize() throws Exception {

        IChunkDescriptor[] chunkArray = new IChunkDescriptor[4];
        Arrays.fill(chunkArray, emptyChunk);

        List<IChunkDescriptor> chunks = Arrays.asList(chunkArray);

        IPieceManager IPieceManager = new PieceManager(new RarestFirstSelector(false), chunks);
        PeerConnection peer = mock(PeerConnection.class);
        assertExceptionWithMessage(
                it -> {
                    IPieceManager.peerHasBitfield(peer, new byte[]{0,0}); return null;},
                "bitfield has wrong size: 2");
    }

    @Test
    public void testPieceManager_GetNextPieces() {

        Verifier verifier3 = new Verifier(),
                 verifier5 = new Verifier();

        IChunkDescriptor chunk3 = mockChunk(blockSize, chunkSize, new byte[]{0,0,0,0}, verifier3),
                         chunk5 = mockChunk(blockSize, chunkSize, new byte[]{0,0,0,0}, verifier5);

        IChunkDescriptor[] chunkArray = new IChunkDescriptor[12];
        Arrays.fill(chunkArray, emptyChunk);
        chunkArray[3] = chunk3;
        chunkArray[5] = chunk5;

        List<IChunkDescriptor> chunks = Arrays.asList(chunkArray);
        IPieceManager IPieceManager = new PieceManager(new RarestFirstSelector(false), chunks);

        Map<Integer, List<PeerConnection>> nextPieces;
        nextPieces = IPieceManager.getNextPieces(1);
        assertEquals(0, nextPieces.size());

        // peer has piece #3
        PeerConnection peer1 = mockPeer(1);
        IPieceManager.peerHasBitfield(peer1, new byte[]{0b1 << (7 - 3), 0});
        nextPieces = IPieceManager.getNextPieces(1);
        assertEquals(1, nextPieces.size());
        assertHasPieceAndPeers(nextPieces, 3, peer1);

        // another peer has pieces #3 and #5
        PeerConnection peer2 = mockPeer(2);
        IPieceManager.peerHasBitfield(peer2, new byte[]{(0b1 << (7 - 3)) + (0b1 << (7 - 5)), 0});
        nextPieces = IPieceManager.getNextPieces(1);
        assertEquals(1, nextPieces.size());
        assertHasPieceAndPeers(nextPieces, 5, peer2); // <-- randomization can affect the result -- don't use it

        verifier5.setVerified();
        nextPieces = IPieceManager.getNextPieces(2);
        assertEquals(1, nextPieces.size());
        assertHasPieceAndPeers(nextPieces, 3, peer1, peer2);

        // yet another peer has pieces #7 and #11
        PeerConnection peer3 = mockPeer(3);
        IPieceManager.peerHasBitfield(peer3, new byte[]{0, 0b1 << (7 - 3)});
        IPieceManager.peerHasPiece(peer3, 7);
        nextPieces = IPieceManager.getNextPieces(3);
        assertEquals(3, nextPieces.size());
        assertHasPieceAndPeers(nextPieces, 3, peer1, peer2);
        assertHasPieceAndPeers(nextPieces, 7, peer3);
        assertHasPieceAndPeers(nextPieces, 11,peer3);

        verifier3.setVerified();
        nextPieces = IPieceManager.getNextPieces(3);
        assertEquals(2, nextPieces.size());
        assertHasPieceAndPeers(nextPieces, 7, peer3);
        assertHasPieceAndPeers(nextPieces, 11, peer3);
    }

    private static IChunkDescriptor mockChunk(long blockSize, long chunkSize, byte[] bitfield,
                                              Supplier<Boolean> verifier) {

        byte[] _bitfield = Arrays.copyOf(bitfield, bitfield.length);

        IChunkDescriptor chunk = mock(IChunkDescriptor.class);
        when(chunk.getSize()).thenReturn(chunkSize);
        when(chunk.getBlockSize()).thenReturn(blockSize);
        when(chunk.getBitfield()).thenReturn(_bitfield);
        when(chunk.getStatus()).thenReturn(statusForBitfield(_bitfield));
        when(chunk.verify()).then(it -> verifier == null? false : verifier.get());

        return chunk;
    }

    private static PeerConnection mockPeer(int id) {
        PeerConnection connection = mock(PeerConnection.class);
        when(connection.isClosed()).thenReturn(false);
        when(connection.getTag()).thenReturn(id);
        return connection;
    }

    private static void assertHasPieceAndPeers(Map<Integer, List<PeerConnection>> nextPieces, Integer pieceIndex,
                                               PeerConnection... peers) {

        assertTrue(nextPieces.containsKey(pieceIndex));
        List<PeerConnection> actualPeers = nextPieces.get(pieceIndex);
        assertEquals(peers.length, actualPeers.size());
        for (PeerConnection peer : peers) {
            assertContains(actualPeers, peer);
        }
    }

    private static void assertContains(Collection<PeerConnection> connections, PeerConnection expected) {

        boolean contains = false;
        for (PeerConnection connection : connections) {
            if (connection.getTag().equals(expected.getTag())) {
                contains = true;
            }
        }
        assertTrue(contains);
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
