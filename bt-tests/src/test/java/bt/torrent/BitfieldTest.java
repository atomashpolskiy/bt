package bt.torrent;

import bt.data.ChunkDescriptor;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class BitfieldTest extends BaseBitfieldTest {

    @Test
    public void testBitfield() {

        List<ChunkDescriptor> chunks = Arrays.asList(completeChunk, emptyChunk, emptyChunk, completeChunk,
                emptyChunk, emptyChunk, emptyChunk, completeChunk);
        Bitfield bitfield = new Bitfield(chunks);

        byte expectedBitfield = (byte) (1 + (0b1 << 4) + (0b1 << 7));

        assertArrayEquals(new byte[]{expectedBitfield}, bitfield.getBitmask());
        assertNotEquals(0, bitfield.getPiecesComplete());
        assertEquals(5, bitfield.getPiecesRemaining());
    }

    @Test
    public void testBitfield_AllEmpty() {

        ChunkDescriptor[] chunkArray = new ChunkDescriptor[12];
        Arrays.fill(chunkArray, emptyChunk);

        List<ChunkDescriptor> chunks = Arrays.asList(chunkArray);
        Bitfield bitfield = new Bitfield(chunks);

        assertArrayEquals(new byte[]{0,0}, bitfield.getBitmask());
        assertEquals(0, bitfield.getPiecesComplete());
        assertEquals(12, bitfield.getPiecesRemaining());
    }
}
