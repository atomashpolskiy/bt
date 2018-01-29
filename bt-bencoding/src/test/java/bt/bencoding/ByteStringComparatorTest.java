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

import org.junit.Test;

import static bt.bencoding.ByteStringComparator.comparator;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ByteStringComparatorTest {

    @Test
    public void testComparator_SameLength() {
        byte[] b1 = new byte[] {1, 2, 3};
        byte[] b2 = new byte[] {2, 2, 3};

        assertTrue(comparator().compare(b1, b2) < 0);
        assertTrue(comparator().compare(b2, b1) > 0);
    }

    @Test
    public void testComparator_DifferentLength() {
        byte[] b1 = new byte[] {1, 2};
        byte[] b2 = new byte[] {1, 2, 3};

        assertTrue(comparator().compare(b1, b2) < 0);
        assertTrue(comparator().compare(b2, b1) > 0);
    }

    @Test
    public void testComparator_BothEmpty() {
        byte[] b1 = new byte[] {};
        byte[] b2 = new byte[] {};

        assertEquals(0, comparator().compare(b1, b2));
        assertEquals(0, comparator().compare(b2, b1));
    }

    @Test
    public void testComparator_OneEmpty() {
        byte[] b1 = new byte[] {};
        byte[] b2 = new byte[] {1, 2, 3};

        assertTrue(comparator().compare(b1, b2) < 0);
        assertTrue(comparator().compare(b2, b1) > 0);
    }

    /**
     * Remember that we're sorting byte strings, not byte arrays - negative numbers go last.
     */
    @Test
    public void testComparator_NegativeNumbers() {
        byte[] b1 = new byte[] {-1, 2, 3};
        byte[] b2 = new byte[] { 1, 2, 3};

        assertTrue(comparator().compare(b1, b2) > 0);
        assertTrue(comparator().compare(b2, b1) < 0);
    }
}
