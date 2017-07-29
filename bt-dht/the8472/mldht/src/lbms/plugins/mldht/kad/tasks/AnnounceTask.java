/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad.tasks;

import lbms.plugins.mldht.kad.DHTConstants;
import lbms.plugins.mldht.kad.KBucketEntry;
import lbms.plugins.mldht.kad.Key;
import lbms.plugins.mldht.kad.Node;
import lbms.plugins.mldht.kad.RPCCall;
import lbms.plugins.mldht.kad.RPCServer;
import lbms.plugins.mldht.kad.messages.AnnounceRequest;
import lbms.plugins.mldht.kad.messages.MessageBase;

import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * @author Damokles
 *
 */
public class AnnounceTask extends TargetedTask {

	private int								port;
	private boolean							isSeed;
	
	NavigableMap<KBucketEntry, byte[]> todo;
	
	public AnnounceTask (RPCServer rpc, Node node,
			Key info_hash, int port, Map<KBucketEntry, byte[]> candidatesAndTokens) {
		super(info_hash, rpc, node);
		this.port = port;
		this.todo = new TreeMap<>(new KBucketEntry.DistanceOrder(info_hash));
		todo.putAll(candidatesAndTokens);
	}

	public void setSeed(boolean isSeed) {
		this.isSeed = isSeed;
	}

	@Override
	void callFinished (RPCCall c, MessageBase rsp) {}
	@Override
	void callTimeout (RPCCall c) {}
	
	@Override
	void update () {
		for(;;) {
			if(getRecvResponses() >= DHTConstants.MAX_ENTRIES_PER_BUCKET)
				return;
			
			RequestPermit p = checkFreeSlot();
			// we don't care about stalls here;
			if(p != RequestPermit.FREE_SLOT)
				return;
			
			Map.Entry<KBucketEntry, byte[]> me = todo.firstEntry();

			if(me == null)
				return;
			
			KBucketEntry e = me.getKey();

			AnnounceRequest anr = new AnnounceRequest(targetKey, port, me.getValue());
			//System.out.println("sending announce to ID:"+e.getID()+" addr:"+e.getAddress());
			anr.setDestination(e.getAddress());
			anr.setSeed(isSeed);
			if(!rpcCall(anr,e.getID(),c -> {
				c.builtFromEntry(e);
				todo.entrySet().remove(me);
			})) {
				break;
			}
		}
	}
	
	@Override
	public int getTodoCount() {
		return todo.size();
	}
	
	@Override
	boolean canDoRequest() {
		// a) we only announce to K nodes, not N; b) wait out the full timeout, not he adaptive one
		return getNumOutstandingRequests() < DHTConstants.MAX_ENTRIES_PER_BUCKET;
	}
	
	@Override
	protected boolean isDone() {
		if(getRecvResponses() >= DHTConstants.MAX_ENTRIES_PER_BUCKET)
			return true;
		if(todo.isEmpty() && getNumOutstandingRequests() == 0)
			return true;
			
		return false;
	}

	/**
	 * @return the info_hash
	 */
	public Key getInfoHash () {
		return targetKey;
	}

	/* (non-Javadoc)
	 * @see lbms.plugins.mldht.kad.Task#start()
	 */
	@Override
	public
	void start () {

		super.start();
	}
}
