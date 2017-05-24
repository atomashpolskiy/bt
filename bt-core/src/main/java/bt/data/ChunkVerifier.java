package bt.data;

import java.util.List;

/**
 * Implements data verification strategy.
 *
 * @since 1.2
 */
public interface ChunkVerifier {

    /**
     * Conducts verification of the provided list of chunks and updates bitfield with the results.
     *
     * @param chunks List of chunks
     * @param bitfield Bitfield
     * @return true if all chunks have been verified successfully (meaning that all data is present and correct)
     * @since 1.2
     */
    boolean verify(List<ChunkDescriptor> chunks, Bitfield bitfield);

    /**
     * Conducts verification of the provided chunk.
     *
     * @param chunk Chunk
     * @return true if the chunk has been verified successfully (meaning that all data is present and correct)
     * @since 1.2
     */
    boolean verify(ChunkDescriptor chunk);
}
