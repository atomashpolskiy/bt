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
