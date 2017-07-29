/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.test.bencode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static the8472.utils.Functional.tap;

import java.util.stream.IntStream;

import org.junit.Test;

import lbms.plugins.mldht.kad.Key;

public class KeyTest {

	@Test
	public void testCompareTo() {
		assertTrue(Key.MIN_KEY.compareTo(Key.MAX_KEY) < 0);
		assertTrue(Key.MAX_KEY.compareTo(Key.MIN_KEY) > 0);
		assertEquals(Key.MAX_KEY.compareTo(Key.MAX_KEY), 0);
		assertTrue(Key.MIN_KEY.compareTo(Key.createRandomKey()) < 0);
	}
		
	@Test
	public void testThreeWayDistance() {
		Key rnd = Key.createRandomKey();
		Key neighbor = new Key(tap(rnd.getHash(), b -> b[15] ^= 0x5a));
		Key far = new Key(tap(rnd.getHash(), b -> b[13] ^= 0x01));
		
		
		assertEquals(rnd.threeWayDistance(neighbor, far) , -1);
		assertEquals(rnd.threeWayDistance(far, neighbor) , 1);
		assertEquals(rnd.threeWayDistance(far, far) , 0);
		assertEquals(rnd.threeWayDistance(neighbor, Key.MAX_KEY) , -1);
		assertEquals(rnd.threeWayDistance(neighbor, Key.MIN_KEY) , -1);
		assertEquals(rnd.threeWayDistance(far, Key.MAX_KEY) , -1);
		assertEquals(rnd.threeWayDistance(far, Key.MIN_KEY) , -1);
		assertEquals(rnd.threeWayDistance(Key.createRandomKey(), far) , 1);
		assertEquals(rnd.threeWayDistance(Key.createRandomKey(), neighbor) , 1);

	}
	
	@Test
	public void testLeadingBit() {
		assertEquals(-1, Key.MIN_KEY.leadingOneBit());
		assertEquals(13, Key.setBit(13).leadingOneBit());
	}
	
	@Test
	public void testHashCode() {
		
		int hammingWeights[] = IntStream.range(0, 512).map(i -> Integer.bitCount(Key.createRandomKey().hashCode())).sorted().toArray();
		int median = hammingWeights[hammingWeights.length/2];
		
		assertEquals(median, 16);
		
	}

}
