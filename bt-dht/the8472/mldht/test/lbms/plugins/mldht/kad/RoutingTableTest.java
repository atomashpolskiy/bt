/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.junit.Test;

import lbms.plugins.mldht.kad.Node.RoutingTable;
import lbms.plugins.mldht.kad.Node.RoutingTableEntry;

public class RoutingTableTest {
	
	RoutingTable table;
	
	
	void test(Function<Key, RoutingTableEntry> f) {
		for(int i = 0; i < 800;i++) {
			Set<RoutingTableEntry> toRemove = new HashSet<>(10);
			List<RoutingTableEntry> toAdd = new ArrayList<>(20);
			
			assertEquals(table.entries[0], f.apply(Key.MIN_KEY));
			assertEquals(table.entries[table.entries.length-1], f.apply(Key.MAX_KEY));
			
			for(int j=0;j<7;j++) {
				Key k = Key.createRandomKey();
				RoutingTableEntry entry = f.apply(k);
				
				assertTrue(entry.prefix.isPrefixOf(k));
				
				assertEquals(entry, f.apply(entry.prefix.first()));
				assertEquals(entry, f.apply(entry.prefix.last()));
				
				if(toRemove.add(entry)) {
					toAdd.add(new RoutingTableEntry(entry.prefix.splitPrefixBranch(false), new KBucket(), (x) -> false));
					toAdd.add(new RoutingTableEntry(entry.prefix.splitPrefixBranch(true), new KBucket(), (x) -> false));

				}
			}
			
			table = table.modify(toRemove, toAdd);
		}
		
	}
	
	
	@Test
	public void testCache() {
		table = new RoutingTable();
		
		test((k) -> table.entryForId(k));
	}

}
