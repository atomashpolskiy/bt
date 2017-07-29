/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad.tasks;

import lbms.plugins.mldht.kad.DHT;
import lbms.plugins.mldht.kad.DHT.DHTtype;
import lbms.plugins.mldht.kad.DHT.LogLevel;
import lbms.plugins.mldht.kad.DHTConstants;
import lbms.plugins.mldht.kad.GenericStorage;
import lbms.plugins.mldht.kad.GenericStorage.StorageItem;
import lbms.plugins.mldht.kad.KBucketEntry;
import lbms.plugins.mldht.kad.KClosestNodesSearch;
import lbms.plugins.mldht.kad.Key;
import lbms.plugins.mldht.kad.Node;
import lbms.plugins.mldht.kad.RPCCall;
import lbms.plugins.mldht.kad.RPCServer;
import lbms.plugins.mldht.kad.messages.GetRequest;
import lbms.plugins.mldht.kad.messages.GetResponse;
import lbms.plugins.mldht.kad.messages.MessageBase;
import lbms.plugins.mldht.kad.utils.AddressUtils;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class GetLookupTask extends IteratingTask {
	
	
	Map<KBucketEntry, byte[]> tokens = new ConcurrentHashMap<>();
	long expectedSequence = -1;
	byte[] salt;
	Consumer<StorageItem> valueHandler;
	
	
	public GetLookupTask(Key target, RPCServer srv, Node n) {
		super(target, srv, n);
	}
	
	public void setValueConsumer(Consumer<StorageItem> handler) {
		valueHandler = handler;
	}
	
	public void setSequence(long i) {
		expectedSequence = i;
	}
	
	public void expectedSalt(byte[] s) {
		salt = s;
	}
	
	AtomicReference<StorageItem> result = new AtomicReference<>();
	
	@Override
	public void start() {
		KClosestNodesSearch kns = new KClosestNodesSearch(targetKey, DHTConstants.MAX_ENTRIES_PER_BUCKET * 4, node.getDHT());
		kns.fill();
		
		todo.addCandidates(null, kns.getEntries());
		
		
		
		super.start();
	}
	
	
	@Override
	void update() {
		for(;;) {
			RequestPermit p = checkFreeSlot();
			if(p == RequestPermit.NONE_ALLOWED)
				return;
			
			KBucketEntry e = todo.next().orElse(null);
			if(e == null)
				return;
			
			if(!new RequestCandidateEvaluator(this, closest, todo, e, inFlight).goodForRequest(p))
				return;
				
			GetRequest r = new GetRequest(targetKey);
			
			r.setWant4(node.getDHT().getType() == DHTtype.IPV4_DHT);
			r.setWant6(node.getDHT().getType() == DHTtype.IPV6_DHT);
			r.setDestination(e.getAddress());
			if(expectedSequence != -1)
				r.setSeq(expectedSequence);
			
			if(!rpcCall(r, e.getID(), c -> {
				c.builtFromEntry(e);
				todo.addCall(c, e);
				int rtt = e.getRTT() * 2;
				if(rtt < DHTConstants.RPC_CALL_TIMEOUT_MAX && rtt < rpc.getTimeoutFilter().getStallTimeout())
					c.setExpectedRTT(rtt);
			})) {
				break;
			}
				
			
			
		}
	}

	@Override
	void callFinished(RPCCall c, MessageBase rsp) {
		if(rsp.getType() != MessageBase.Type.RSP_MSG || rsp.getMethod() != MessageBase.Method.GET)
			return;
		
		GetResponse get = (GetResponse) rsp;
		
		KBucketEntry e = todo.acceptResponse(c);

		if(e == null)
			return;
		
		StorageItem data = null;
		
		if(get.getRawValue() != null) {
			Key k = GenericStorage.fingerprint(get.getPubkey(), salt, get.getRawValue());
			
			if(!k.equals(targetKey)) {
				DHT.log("get response fingerprint mismatch " + rsp , LogLevel.Error);
				return;
			}
			
			
			
			if(expectedSequence < 0 || get.getSequenceNumber() >= expectedSequence) {
				data = new StorageItem(get, salt);
				
				if(data.mutable() && !data.validateSig()) {
					DHT.log("signature mismatch", LogLevel.Error);
					return;
				}
			}
			
		}
		
		
		if(data != null) {
			final StorageItem newData = data;
			StorageItem merged = result.updateAndGet(current -> current == null || newData.seq() > current.seq() ? newData  : current);
			if(valueHandler != null && merged == newData) {
				valueHandler.accept(merged);
			}
		}
		
		
		
		Collection<KBucketEntry> returnedNodes = get.getNodes(node.getDHT().getType()).entries().filter(ne -> !AddressUtils.isBogon(ne.getAddress()) && !node.isLocalId(ne.getID())).collect(Collectors.toList());
		
		todo.addCandidates(e, returnedNodes);
		
		if(get.getToken() != null) {
			closest.insert(e);
			tokens.put(e, get.getToken());
		}

	}
	
	public Map<KBucketEntry, byte[]> getTokens() {
		return tokens;
	}

	@Override
	void callTimeout(RPCCall c) {
		// TODO Auto-generated method stub

	}

	@Override
	protected boolean isDone() {
		int waitingFor = getNumOutstandingRequests();
		
		if(waitingFor > 0)
			return false;
		
		KBucketEntry next = todo.next().orElse(null);
		
		if(next == null)
			return true;
		
		
		return new RequestCandidateEvaluator(this, closest, todo, next, inFlight).terminationPrecondition();
	}

}
