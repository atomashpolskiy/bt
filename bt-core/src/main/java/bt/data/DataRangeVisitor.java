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

/**
 * Traverses a data range on a per-unit basis.
 *
 * @since 1.2
 */
public interface DataRangeVisitor {

    /**
     * Visit (a part of) a file in a range of files
     * @param unit A storage unit
     * @param off Offset that designates the beginning of this chunk's part in the file, inclusive;
     *            visitor must not access the file before this index
     * @param lim Limit that designates the end of this chunk's part in the file, exclusive;
     *            visitor must not access the file at or past this index
     *            (i.e. the limit does not belong to this chunk)
     * @return true if next file should be visited; false to stop
     *
     * @since 1.2
     */
    boolean visitUnit(StorageUnit unit, long off, long lim);
}
