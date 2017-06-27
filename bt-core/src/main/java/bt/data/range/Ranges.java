package bt.data.range;

import bt.data.BlockSet;
import bt.data.DataRange;

import java.util.function.Function;

/**
 * @since 1.3
 */
public class Ranges {

    /**
     * @since 1.3
     */
    public static <T extends Range<T>> BlockRange<T> blockRange(T range, long blockSize) {
        return new BlockRange<>(range, blockSize);
    }

    /**
     * @since 1.3
     */
    public static <T extends Range<T>> Range<T> synchronizedRange(T range) {
        return new SynchronizedRange<>(range);
    }

    /**
     * @since 1.3
     */
    public static <T extends Range<T>> Range<BlockRange<T>> synchronizedBlockRange(T range, long blockSize) {
        return new SynchronizedRange<>(blockRange(range, blockSize));
    }

    /**
     * @since 1.3
     */
    public static <T extends Range<T>> DataRange synchronizedDataRange(T range, Function<T, DataRange> converter) {
        SynchronizedRange<T> synchronizedRange = new SynchronizedRange<>(range);
        return new SynchronizedDataRange<>(synchronizedRange, converter::apply);
    }

    /**
     * @since 1.3
     */
    public static DataRange synchronizedDataRange(BlockRange<DataRange> range) {
        SynchronizedRange<BlockRange<DataRange>> synchronizedRange = new SynchronizedRange<>(range);
        return new SynchronizedDataRange<>(synchronizedRange, BlockRange::getDelegate);
    }

    /**
     * @since 1.3
     */
    public static DataRange synchronizedDataRange(DataRange range) {
        SynchronizedRange<DataRange> synchronizedRange = new SynchronizedRange<>(range);
        return new SynchronizedDataRange<>(synchronizedRange, Function.identity());
    }

    /**
     * @since 1.3
     */
    public static BlockSet synchronizedBlockSet(BlockSet blockSet) {
        return new SynchronizedBlockSet(blockSet);
    }

    /**
     * @since 1.3
     */
    public static DataRange dataRange(BlockRange<DataRange> range) {
        return DelegatingDataRange.range(range, DelegatingRange::getDelegate);
    }
}
