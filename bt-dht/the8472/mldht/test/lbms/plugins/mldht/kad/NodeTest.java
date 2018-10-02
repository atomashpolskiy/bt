/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import the8472.mldht.Diagnostics;
import the8472.utils.NeverRunsExecutor;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.Test;

import lbms.plugins.mldht.kad.DHT.DHTtype;
import lbms.plugins.mldht.kad.Node.InsertOptions;
import lbms.plugins.mldht.kad.Node.RoutingTable;
import lbms.plugins.mldht.kad.Node.RoutingTableEntry;
import lbms.plugins.mldht.utils.NIOConnectionManager;

public class NodeTest {
	
	Node node;
	
	void setup(DHTtype type) {
		DHT dht = NodeFactory.buildDHT(type);
		node = dht.getNode();
		node.initKey(null);
		
		NodeFactory.fillTable(node);
	}
	
	
	@Test
	public void testBucketMerges() {
		setup(DHTtype.IPV6_DHT);
		
		Prefix p = new Prefix(Key.createRandomKey(), 20);
		
		List<KBucketEntry> added = new ArrayList<>();
		
		for(int i=0;i<100;i++) {
			KBucketEntry e = new KBucketEntry(new InetSocketAddress(NodeFactory.generateIp(DHTtype.IPV6_DHT,(byte) 0), 1337), p.createRandomKeyFromPrefix());
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
	
	@Test
	public void testReplacementPings() throws UnknownHostException {
		setup(DHTtype.IPV4_DHT);
		
		node.getDHT().setScheduler(new NeverRunsExecutor());
		// a bit hacky. instantiates a v4 address on a v6 node, but travis doesn't support ipv6 at the moment.
		node.getDHT().getServerManager().newServer(InetAddress.getByName("127.0.0.1"));
		node.getDHT().connectionManager = new NIOConnectionManager("test");
		RPCServer srv = node.getDHT().getServerManager().getRandomServer();
		srv.start();
		node.updateHomeBuckets();
		RoutingTable table = node.table();
		Diagnostics diag = new Diagnostics();
		diag.formatRoutingTable(System.out, node);
		
		RoutingTableEntry homeBucket = Arrays.stream(table.entries).filter(e -> e.homeBucket).findAny().get();
		
		KBucketEntry replacement = new KBucketEntry(new InetSocketAddress(NodeFactory.generateIp(DHTtype.IPV4_DHT, (byte) 0),  13), homeBucket.prefix.createRandomKeyFromPrefix());
		homeBucket.bucket.insertInReplacementBucket(replacement);
		
		node.doBucketChecks(0);
		assertEquals(0, node.getDHT().getTaskManager().getNumQueuedTasks());
		assertEquals(0, node.getDHT().getTaskManager().getNumTasks());

		homeBucket.bucket.removeEntryIfBad(homeBucket.bucket.randomEntry().get(), true);
		
		node.doBucketChecks(DHTConstants.BOOTSTRAP_MIN_INTERVAL);
		assertEquals(1, node.getDHT().getTaskManager().getNumQueuedTasks());
		
		srv.stop();
	}

}
