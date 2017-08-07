/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static the8472.utils.Functional.unchecked;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Executors;

import org.junit.Before;
import org.junit.Test;

import lbms.plugins.mldht.kad.Node.RoutingTableEntry;
import lbms.plugins.mldht.kad.messages.PingRequest;
import lbms.plugins.mldht.kad.messages.PingResponse;
import the8472.utils.io.NetMask;

public class OnInsertValidations {
	
	Node node;
	Key nodeId;
	
	@Before
	public void setup() {
		
		DHT dht = NodeFactory.buildDHT();
		dht.setScheduler(Executors.newScheduledThreadPool(0));
		node = dht.getNode();
		node.initKey(null);
		nodeId = node.registerId();
	}
	
	private PingResponse buildResponse(Key k, InetSocketAddress origin) {
		PingRequest req = new PingRequest();
		RPCCall call = new RPCCall(req);
		PingResponse rsp = new PingResponse(new byte[0]);
		rsp.setAssociatedCall(call);
		rsp.setID(k);
		rsp.setOrigin(origin);
		
		return rsp;
	}

	@Test
	public void testImmediateEvictionOnIdMismatch() {
		NodeFactory.fillTable(node);
		KBucket bucket = node.table().get(0).getBucket();
		KBucketEntry entry = bucket.randomEntry().get();
		
		PingResponse rsp = buildResponse(Key.createRandomKey(), entry.getAddress());
		rsp.getAssociatedCall().setExpectedID(entry.getID());

		node.recieved(rsp);

		assertFalse(bucket.findByIPorID(entry.getAddress().getAddress(), entry.getID()).isPresent());
		assertFalse(bucket.findByIPorID(null, rsp.getID()).isPresent());
	}
	
	@Test
	public void testRTTPreference() {
		NodeFactory.fillTable(node);
		
		Collection<Key> localIds = node.localIDs();
		
				
		RoutingTableEntry nonLocalFullBucket = node.table().stream().filter(e -> e.prefix.depth == 1).findAny().get();
		
		Key newId = nonLocalFullBucket.prefix.createRandomKeyFromPrefix();
		PingResponse rsp = buildResponse(newId, new InetSocketAddress(NodeFactory.generateIp((byte)0x00), 1234));
		
		node.recieved(rsp);
		
		// doesn't get inserted because the replacement buckets only overwrite entries once every second and the main bucket is stable anyway
		assertFalse(nonLocalFullBucket.getBucket().getEntries().stream().anyMatch(e -> e.getID().equals(newId)));
		assertFalse(nonLocalFullBucket.getBucket().getReplacementEntries().stream().anyMatch(e -> e.getID().equals(newId)));
		
		long now = System.currentTimeMillis();
		RPCCall call = rsp.getAssociatedCall();
		call.sentTime = now - 50;
		call.responseTime = now;
		
		node.recieved(rsp);
		
		// main bucket accepts one RTT-based replacement for the youngest entry
		assertTrue(nonLocalFullBucket.getBucket().getEntries().stream().anyMatch(e -> e.getID().equals(newId)));
		
		Key anotherId = nonLocalFullBucket.prefix.createRandomKeyFromPrefix();
		rsp = buildResponse(anotherId, new InetSocketAddress(NodeFactory.generateIp((byte)0x00), 1234));
		call = rsp.getAssociatedCall();
		call.sentTime = now - 50;
		call.responseTime = now;
		
		node.recieved(rsp);
		
		// replacement bucket accepts RTT-based overwrite once main bucket is satisfied
		assertTrue(nonLocalFullBucket.getBucket().getReplacementEntries().stream().anyMatch(e -> e.getID().equals(anotherId)));
	}
	
	@Test
	public void testTrustedNodes() {
		NodeFactory.fillTable(node);
		
		Collection<Key> localIds = node.localIDs();
		
		RoutingTableEntry nonLocalFullBucket = node.table().stream().filter(e -> e.prefix.depth == 1).findAny().get();
		
		PingRequest req = new PingRequest();
		RPCCall call = new RPCCall(req);
		PingResponse rsp = new PingResponse(new byte[0]);
		rsp.setAssociatedCall(call);
		Key newId = nonLocalFullBucket.prefix.createRandomKeyFromPrefix();
		rsp.setID(newId);
		rsp.setOrigin(new InetSocketAddress(NodeFactory.generateIp((byte)0x02), 1234));
		
		node.recieved(rsp);
		assertFalse(node.table().stream().anyMatch(e -> e.getBucket().findByIPorID(null, newId).isPresent()));
		
		byte[] addr = new byte[16];
		addr[0] = 0x20;
		addr[1] = 0x01;
		addr[2] = 0x20;
		addr[3] = 0x02;
		
		NetMask mask = new NetMask(unchecked(() -> InetAddress.getByAddress(addr)), 32);
		
		node.setTrustedNetMasks(Collections.singleton(mask));
		
		node.recieved(rsp);
		assertTrue(node.table().stream().anyMatch(e -> e.getBucket().findByIPorID(null, newId).isPresent()));
		
	}
}
