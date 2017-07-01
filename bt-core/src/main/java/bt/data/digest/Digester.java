package bt.data.digest;

import bt.data.DataRange;
import bt.data.range.Range;

/**
 * Calculates hash of some binary data.
 * Implementations may use different hashing algorithms.
 *
 * @since 1.2
 */
public interface Digester {

    /**
     * Calculates hash of a data range.
     *
     * @return Hash (depends on the algorithm being used)
     *
     * @since 1.2
     */
    byte[] digest(DataRange data);

    /**
     * Calculates hash of a binary range.
     *
     * @return Hash (depends on the algorithm being used)
     *
     * @since 1.3
     */
    byte[] digest(Range<?> data);
}
