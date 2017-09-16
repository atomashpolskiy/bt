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

package bt.torrent.selector;

import java.util.Comparator;

/**
 * First compares the least significant 32 bits of Long values,
 * and if they are equal compares the most significant 32 bits.
 *
 * @since 1.1
 */
public class PackedIntComparator implements Comparator<Long> {

    @Override
    public int compare(Long o1, Long o2) {
        if (o1.intValue() > o2.intValue()) {
            return 1;
        } else if (o1.intValue() < o2.intValue()) {
            return -1;
        } else {
            return Long.compare(o1 >> 32, o2 >> 32);
        }
    }
}
