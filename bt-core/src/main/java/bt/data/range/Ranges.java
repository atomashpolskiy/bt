/*
 * Copyright (c) 2016—2017 Andrei Tomashpolskiy and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
    public static DataRange synchronizedDataRange(DataRange range) {
        SynchronizedRange<DataRange> synchronizedRange = new SynchronizedRange<>(range);
        return new SynchronizedDataRange<>(synchronizedRange, Function.identity());
    }

    /**
     * @since 1.3
     */
    public static DataRange dataRange(BlockRange<DataRange> range) {
        return DelegatingDataRange.range(range, DelegatingRange::getDelegate);
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
    public static BlockSet synchronizedBlockSet(BlockSet blockSet) {
        return new SynchronizedBlockSet(blockSet);
    }
}
