/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.utils;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ArraysTest {

	@Test
	public void testCompareUnsigned() {
		assertTrue(Arrays.compareUnsigned(new byte[] {0x0A}, new byte[] {0x0A}) == 0);
		assertTrue(Arrays.compareUnsigned(new byte[] {0x00}, new byte[] {0x01}) == -1);
		// length
		assertTrue(Arrays.compareUnsigned(new byte[] {0x00}, new byte[] {0x00,0x00}) == -1);
		// unsignedness
		assertTrue(Arrays.compareUnsigned(new byte[] {(byte) 0xFE}, new byte[] {(byte) 0xFF}) == -1);
		assertTrue(Arrays.compareUnsigned(new byte[] {(byte) 0x01}, new byte[] {(byte) 0xFF}) < 0);
		// 8 bytes
		assertTrue(Arrays.compareUnsigned(new byte[] {0x00,0x00,0x00,0x00,0x00,0x00,0x00, (byte) 0xFE}, new byte[] {0x00,0x00,0x00,0x00,0x00,0x00,0x00,(byte) 0xFF}) == -1);
		assertTrue(Arrays.compareUnsigned(new byte[] {0x00,0x00,0x00,0x00,0x00,0x00,0x00, (byte) 0x01}, new byte[] {0x00,0x00,0x00,0x00,0x00,0x00,0x00,(byte) 0xFF}) < 0);
		// 9 bytes
		assertTrue(Arrays.compareUnsigned(new byte[] {0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00, (byte) 0x01}, new byte[] {0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,(byte) 0xFF}) < 0);
	}

}
