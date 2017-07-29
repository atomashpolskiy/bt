/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad.tasks;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;

import lbms.plugins.mldht.kad.DHT.DHTtype;
import lbms.plugins.mldht.kad.KBucketEntry;
import lbms.plugins.mldht.kad.Key;
import lbms.plugins.mldht.kad.Node;
import lbms.plugins.mldht.kad.Node.RoutingTableEntry;
import lbms.plugins.mldht.kad.NodeList;
import lbms.plugins.mldht.kad.RPCCall;
import lbms.plugins.mldht.kad.RPCServer;
import lbms.plugins.mldht.kad.messages.FindNodeRequest;
import lbms.plugins.mldht.kad.messages.FindNodeResponse;
import lbms.plugins.mldht.kad.messages.MessageBase;
import lbms.plugins.mldht.kad.messages.MessageBase.Method;
import lbms.plugins.mldht.kad.messages.MessageBase.Type;

/**
 * @author The 8472
 *
 */
public class KeyspaceCrawler extends Task {
	
	Set<InetSocketAddress> responded = new HashSet<>();
	Set<KBucketEntry> todo = new HashSet<>();
	Set<InetSocketAddress> visited = new HashSet<>();
	
	KeyspaceCrawler (RPCServer rpc, Node node) {
		super(rpc, node);
		setInfo("Exhaustive Keyspace Crawl");
		addListener(t -> done());
	}
	
	@Override
	public int getTodoCount() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	void update () {
		// go over the todo list and send find node calls
		// until we have nothing left

			while (canDoRequest()) {
				synchronized (todo) {

				KBucketEntry e = todo.stream().findAny().orElse(null);
				if(e == null)
					break;
				
				if (visited.contains(e.getAddress()))
					continue;
				
				// send a findNode to the node
				FindNodeRequest fnr;

				fnr = new FindNodeRequest(Key.createRandomKey());
				fnr.setWant4(rpc.getDHT().getType() == DHTtype.IPV4_DHT);
				fnr.setWant6(rpc.getDHT().getType() == DHTtype.IPV6_DHT);
				fnr.setDestination(e.getAddress());
				rpcCall(fnr,e.getID(), c -> {
					todo.remove(e);
					visited.add(e.getAddress());
				});
			}
		}
	}

	@Override
	void callFinished (RPCCall c, MessageBase rsp) {
		if (isFinished()) {
			return;
		}

		// check the response and see if it is a good one
		if (rsp.getMethod() != Method.FIND_NODE || rsp.getType() != Type.RSP_MSG)
			return;

		FindNodeResponse fnr = (FindNodeResponse) rsp;

		responded.add(fnr.getOrigin());

		NodeList nodes = fnr.getNodes(rpc.getDHT().getType());
		if (nodes == null)
			return;
		
		synchronized (todo)
		{
			nodes.entries().filter(e -> !node.isLocalId(e.getID()) && !todo.contains(e) && visited.contains(e.getAddress())).forEach(todo::add);
		}


	}
	
	@Override
	public int requestConcurrency() {
		// TODO Auto-generated method stub
		return super.requestConcurrency() * 5;
	}
	
	@Override
	protected boolean isDone() {
		if (todo.size() == 0 && getNumOutstandingRequests() == 0 && !isFinished()) {
			return true;
		}
		return false;
	}

	@Override
	void callTimeout (RPCCall c) {

	}

	/* (non-Javadoc)
	 * @see lbms.plugins.mldht.kad.Task#start()
	 */
	@Override
	public
	void start() {
		int added = 0;

		// delay the filling of the todo list until we actually start the task
		
		outer: for (RoutingTableEntry bucket : node.table().list())
			for (KBucketEntry e : bucket.getBucket().getEntries())
				if (e.eligibleForLocalLookup())
				{
					todo.add(e);
					added++;
				}
		super.start();
	}

	
	private void done () {
		System.out.println("crawler done, seen "+responded.size());
	}
}
