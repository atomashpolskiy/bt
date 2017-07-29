/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.Before;
import org.junit.Test;

import lbms.plugins.mldht.kad.Node.InsertOptions;

public class NodeTest {
	
	Node node;
	
	@Before
	public void setup() {
		DHT dht = NodeFactory.buildDHT();
		node = dht.getNode();
		node.initKey(null);
		
		NodeFactory.fillTable(node);
	}
	
	
	@Test
	public void testBucketMerges() {
		
		Prefix p = new Prefix(Key.createRandomKey(), 20);
		
		List<KBucketEntry> added = new ArrayList<>();
		
		
		for(int i=0;i<100;i++) {
			KBucketEntry e = new KBucketEntry(new InetSocketAddress(NodeFactory.generateIp((byte) 0), 1337), p.createRandomKeyFromPrefix());
			added.add(e);
			e.signalResponse(0);
			assertTrue(e.verifiedReachable());
			assertTrue(e.eligibleForNodesList());
			assertFalse(e.removableWithoutReplacement());
			node.insertEntry(e, EnumSet.of(InsertOptions.ALWAYS_SPLIT_IF_FULL, InsertOptions.FORCE_INTO_MAIN_BUCKET));
		}

		
		// new Diagnostics().formatRoutingTable(System.out, node);
		

		assertTrue(node.table().entryForId(p).prefix.getDepth() > p.getDepth());
		
		node.mergeBuckets();
		
		assertTrue(node.table().entryForId(p).prefix.getDepth() > p.getDepth());
		
		added.forEach(e -> {
			e.signalScheduledRequest();
			IntStream.rangeClosed(0, KBucketEntry.MAX_TIMEOUTS).forEach(x -> e.signalRequestTimeout());
			assertTrue(e.removableWithoutReplacement());
		});

		
		node.mergeBuckets();
		
		assertTrue(node.table().entryForId(p).prefix.getDepth() < p.getDepth());
			
		
		
	}

}
