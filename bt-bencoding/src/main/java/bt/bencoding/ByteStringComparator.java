/*
 * Copyright (c) 2016—2018 Andrei Tomashpolskiy and individual contributors.
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

package bt.bencoding;

import java.util.Comparator;

/**
 * Sorts byte arrays as if they were raw strings.
 *
 * This means that negative numbers go after positive numbers,
 * because they represent higher order characters (128-255).
 */
class ByteStringComparator implements Comparator<byte[]> {

    private static final ByteStringComparator instance = new ByteStringComparator();

    static ByteStringComparator comparator() {
        return instance;
    }

    @Override
    public int compare(byte[] o1, byte[] o2) {
        for (int i = 0, j = 0; i < o1.length && j < o2.length; i++, j++) {
            int k = (o1[i] & 0xFF) - (o2[j] & 0xFF);
            if (k != 0) {
                return k;
            }
        }
        return o1.length - o2.length;
    }
}
