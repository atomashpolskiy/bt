/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad.tasks;

import lbms.plugins.mldht.kad.DHTConstants;
import lbms.plugins.mldht.kad.GenericStorage.StorageItem;
import lbms.plugins.mldht.kad.KBucketEntry;
import lbms.plugins.mldht.kad.Node;
import lbms.plugins.mldht.kad.RPCCall;
import lbms.plugins.mldht.kad.RPCServer;
import lbms.plugins.mldht.kad.messages.MessageBase;
import lbms.plugins.mldht.kad.messages.PutRequest;

import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public class PutTask extends TargetedTask {
	
	NavigableMap<KBucketEntry, byte[]> todo;
	StorageItem toPut;
	
	
	
	public PutTask(RPCServer rpc, Node node, Map<KBucketEntry, byte[]> candidatesAndTokens, StorageItem it) {
		super(it.fingerprint(), rpc, node);
		toPut = it;
		todo = new TreeMap<>(new KBucketEntry.DistanceOrder(targetKey));
		todo.putAll(candidatesAndTokens);
	}
	
	@Override
	void update() {
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
			
			PutRequest put = new PutRequest();
			
			put.populateFromStorage(toPut);

			put.setDestination(e.getAddress());
			put.setToken(me.getValue());

			if(!rpcCall(put,e.getID(),c -> {
				c.builtFromEntry(e);
				todo.entrySet().remove(me);
			})) {
				break;
			}
			
		}
	}

	@Override
	void callFinished(RPCCall c, MessageBase rsp) {
		// TODO Auto-generated method stub
	}

	@Override
	void callTimeout(RPCCall c) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public int getTodoCount() {
		return todo.size();
	}

	@Override
	protected boolean isDone() {
		if(getRecvResponses() >= DHTConstants.MAX_ENTRIES_PER_BUCKET)
			return true;
		if(todo.isEmpty() && getNumOutstandingRequests() == 0)
			return true;
			
		return false;
	}

}
