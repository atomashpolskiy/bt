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

package bt.data;

import bt.data.range.Range;

/**
 * Represents a range of binary data, abstracting the mapping of data onto the storage layer.
 * Real data may span over several storage units or reside completely inside a single storage unit.
 *
 * @since 1.2
 */
public interface DataRange extends Range<DataRange> {

    /**
     * Traverse the storage units in this data range.
     *
     * @since 1.2
     */
    void visitUnits(DataRangeVisitor visitor);

    /**
     * {@inheritDoc}
     *
     * @since 1.3
     */
    DataRange getSubrange(long offset, long length);

    /**
     * {@inheritDoc}
     *
     * @since 1.3
     */
    DataRange getSubrange(long offset);
}
