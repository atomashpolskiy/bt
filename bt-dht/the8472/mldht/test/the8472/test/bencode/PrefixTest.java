/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.test.bencode;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static the8472.utils.Functional.tap;

import java.util.Arrays;

import org.junit.Test;

import lbms.plugins.mldht.kad.Key;
import lbms.plugins.mldht.kad.Prefix;

public class PrefixTest {

	@Test
	public void testPrefixMatch() {
		
		byte[] reference = new byte[20];
		
		reference[0] = 0x10; // bit 4
		
		Key k = new Key(reference);
		Prefix p = new Prefix(k, 3);
		
		assertTrue(Arrays.equals(p.getHash(), reference));
		assertTrue(Arrays.equals(p.getParentPrefix().getHash(), new byte[20]));
		assertTrue(Arrays.equals(p.splitPrefixBranch(false).getHash(), tap(new byte[20], b -> b[0] = 0x10)));
		assertTrue(Arrays.equals(p.splitPrefixBranch(true).getHash(), tap(new byte[20], b -> b[0] = 0x18)));
		assertTrue(p.isPrefixOf(k));
		assertTrue(p.isPrefixOf(new Key(tap(new byte[20], b -> b[0] = 0x11))));
		assertTrue(p.isPrefixOf(new Key(tap(new byte[20], b -> b[0] = 0x1f))));
		assertFalse(p.isPrefixOf(new Key(tap(new byte[20], b -> b[0] = 0x20))));
		assertFalse(p.isPrefixOf(new Key(tap(new byte[20], b -> b[0] = (byte) 0x7f))));
		
	}

}
