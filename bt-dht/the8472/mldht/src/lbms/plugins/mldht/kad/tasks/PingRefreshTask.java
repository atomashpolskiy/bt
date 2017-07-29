/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad.tasks;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import lbms.plugins.mldht.kad.DHT;
import lbms.plugins.mldht.kad.KBucket;
import lbms.plugins.mldht.kad.KBucketEntry;
import lbms.plugins.mldht.kad.Node;
import lbms.plugins.mldht.kad.RPCCall;
import lbms.plugins.mldht.kad.RPCServer;
import lbms.plugins.mldht.kad.messages.MessageBase;
import lbms.plugins.mldht.kad.messages.PingRequest;

/**
 * @author Damokles
 *
 */
public class PingRefreshTask extends Task {

	private boolean							cleanOnTimeout;
	boolean									alsoCheckGood	= false;
	boolean 								probeReplacement = false;
	Deque<KBucketEntry>				todo;
	Set<KBucketEntry> visited;
	private Map<MessageBase, KBucketEntry>	lookupMap;
	KBucket									bucket;

	/**
	 * @param rpc
	 * @param node
	 * @param bucket the bucket to refresh
	 * @param cleanOnTimeout if true Nodes that fail to respond are removed. should be false for normal use.
	 */
	public PingRefreshTask (RPCServer rpc, Node node, KBucket bucket, boolean cleanOnTimeout) {
		super(rpc, node);
		this.cleanOnTimeout = cleanOnTimeout;
		todo = new ArrayDeque<>();
		visited = new HashSet<>();
		lookupMap = new HashMap<>();
		
		addBucket(bucket);
	}
	
	public void checkGoodEntries(boolean val) {
		alsoCheckGood = val;
	}
	
	public void probeUnverifiedReplacement(boolean val) {
		probeReplacement = true;
	}
	
	public void addBucket(KBucket bucket) {
		if (bucket != null) {
			if(this.bucket !=null)
				throw new IllegalStateException("a bucket already present");
			this.bucket = bucket;
			bucket.updateRefreshTimer();
			for (KBucketEntry e : bucket.getEntries()) {
				if (e.needsPing() || cleanOnTimeout || alsoCheckGood) {
					todo.add(e);
				}
			}
			
			if(probeReplacement) {
				bucket.findPingableReplacement().ifPresent(todo::add);
			}
				
		}
	}

	/* (non-Javadoc)
	 * @see lbms.plugins.mldht.kad.Task#callFinished(lbms.plugins.mldht.kad.RPCCallBase, lbms.plugins.mldht.kad.messages.MessageBase)
	 */
	@Override
	void callFinished (RPCCall c, MessageBase rsp) {
		// most of the success handling is done by bucket maintenance
		synchronized (lookupMap) {
			KBucketEntry e = lookupMap.remove(c.getRequest());
		}
	}

	/* (non-Javadoc)
	 * @see lbms.plugins.mldht.kad.Task#callTimeout(lbms.plugins.mldht.kad.RPCCallBase)
	 */
	@Override
	void callTimeout (RPCCall c) {
		MessageBase mb = c.getRequest();

		synchronized (lookupMap) {
			KBucketEntry e = lookupMap.remove(mb);
			if(e == null)
				return;
			
			KBucket bucket = node.table().entryForId(e.getID()).getBucket();
			if (bucket != null) {
				if(cleanOnTimeout) {
					DHT.logDebug("Removing invalid entry from cache.");
					bucket.removeEntryIfBad(e, true);
				}
			}
		}

	}
	
	@Override
	public int getTodoCount() {
		return todo.size();
	}
	
	@Override
	void update () {
		if(todo.isEmpty()) {
			bucket.entriesStream().filter(KBucketEntry::needsPing).filter(e -> !lookupMap.values().contains(e)).forEach(todo::add);
		}
		
		while(!todo.isEmpty() && canDoRequest()) {
			KBucketEntry e = todo.peekFirst();

			if (visited.contains(e) || (!alsoCheckGood && !e.needsPing())) {
				todo.remove(e);
				continue;
			}

			PingRequest pr = new PingRequest();
			pr.setDestination(e.getAddress());
			
			if(!rpcCall(pr,e.getID(),c -> {
				c.builtFromEntry(e);
				synchronized (lookupMap) {
					lookupMap.put(pr, e);
				}
				visited.add(e);
				todo.remove(e);
			})) {
				break;
			}
				
		}
	}
	
	@Override
	protected boolean isDone() {
		return todo.isEmpty() && getNumOutstandingRequests() == 0 && !isFinished();
	}
}
