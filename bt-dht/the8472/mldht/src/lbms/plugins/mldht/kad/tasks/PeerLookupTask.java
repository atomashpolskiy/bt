/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad.tasks;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static java.lang.Math.min;

import lbms.plugins.mldht.kad.AnnounceNodeCache;
import lbms.plugins.mldht.kad.DBItem;
import lbms.plugins.mldht.kad.DHT;
import lbms.plugins.mldht.kad.DHT.DHTtype;
import lbms.plugins.mldht.kad.DHT.LogLevel;
import lbms.plugins.mldht.kad.DHTConstants;
import lbms.plugins.mldht.kad.KBucketEntry;
import lbms.plugins.mldht.kad.KClosestNodesSearch;
import lbms.plugins.mldht.kad.Key;
import lbms.plugins.mldht.kad.Node;
import lbms.plugins.mldht.kad.NodeList;
import lbms.plugins.mldht.kad.PeerAddressDBItem;
import lbms.plugins.mldht.kad.RPCCall;
import lbms.plugins.mldht.kad.RPCServer;
import lbms.plugins.mldht.kad.ScrapeResponseHandler;
import lbms.plugins.mldht.kad.messages.GetPeersRequest;
import lbms.plugins.mldht.kad.messages.GetPeersResponse;
import lbms.plugins.mldht.kad.messages.MessageBase;
import lbms.plugins.mldht.kad.messages.MessageBase.Method;
import lbms.plugins.mldht.kad.utils.AddressUtils;

/**
 * @author Damokles
 *
 */
public class PeerLookupTask extends IteratingTask {

	private boolean							noAnnounce;
	private boolean							noSeeds;
	private boolean							fastTerminate;
	
	// nodes which have answered with tokens
	private Map<KBucketEntry, byte[]>		announceCanidates;
	private ScrapeResponseHandler			scrapeHandler;
	BiConsumer<KBucketEntry, PeerAddressDBItem>				resultHandler = (x,y) -> {};
	
	private Set<PeerAddressDBItem>			returnedItems;
	
	AnnounceNodeCache						cache;
	boolean									useCache = true;



	public PeerLookupTask (RPCServer rpc, Node node,
			Key info_hash) {
		super(info_hash, rpc, node);
		announceCanidates = new ConcurrentHashMap<>();
		returnedItems = Collections.newSetFromMap(new ConcurrentHashMap<PeerAddressDBItem, Boolean>());

		cache = rpc.getDHT().getCache();
		// register key even before the task is started so the cache can already accumulate entries
		cache.register(targetKey,false);

		addListener(t -> updatePopulationEstimator());
		
	}

	public void setScrapeHandler(ScrapeResponseHandler scrapeHandler) {
		this.scrapeHandler = scrapeHandler;
	}
	
	public void useCache(boolean c) {
		useCache = c;
	}
	
	public void setResultHandler(BiConsumer<KBucketEntry,PeerAddressDBItem> handler) {
		resultHandler = handler;
	}
	
	public void setNoSeeds(boolean avoidSeeds) {
		noSeeds = avoidSeeds;
	}
	
	/**
	 * enabling this also enables noAnnounce
	 */
	public void setFastTerminate(boolean fastTerminate) {
		if(!state.get().preStart())
			throw new IllegalStateException("cannot change lookup mode after startup");
		this.fastTerminate = fastTerminate;
		todo.allowRetransmits(!fastTerminate);
		if(fastTerminate)
			setNoAnnounce(true);
	}
	
	public void filterKnownUnreachableNodes(boolean toggle) {
		if(toggle)
			todo.setNonReachableCache(node.getDHT().getUnreachableCache());
		else
			todo.setNonReachableCache(null);
	}

	public void setNoAnnounce(boolean noAnnounce) {
		this.noAnnounce = noAnnounce;
	}
	
	public boolean isNoAnnounce() {
		return noAnnounce;
	}

	/* (non-Javadoc)
	 * @see lbms.plugins.mldht.kad.Task#callFinished(lbms.plugins.mldht.kad.RPCCall, lbms.plugins.mldht.kad.messages.MessageBase)
	 */
	@Override
	void callFinished (RPCCall c, MessageBase rsp) {
		if (c.getMessageMethod() != Method.GET_PEERS) {
			return;
		}
		
		GetPeersResponse gpr = (GetPeersResponse) rsp;
		
		KBucketEntry match = todo.acceptResponse(c);
		
		if(match == null)
			return;
		
		Set<KBucketEntry> returnedNodes = new HashSet<>();
		
		NodeList nodes = gpr.getNodes(rpc.getDHT().getType());
		
		if (nodes != null)
		{
			nodes.entries().filter(e -> !AddressUtils.isBogon(e.getAddress()) && !node.isLocalId(e.getID())).forEach(e -> {
				returnedNodes.add(e);
			});
		}
		
		todo.addCandidates(match, returnedNodes);
		
		List<DBItem> items = gpr.getPeerItems();
		//if(items.size() > 0)
		//	System.out.println("unique:"+new HashSet<DBItem>(items).size()+" all:"+items.size()+" ver:"+gpr.getVersion()+" entries:"+items);
		for (DBItem item : items)
		{
			if(!(item instanceof PeerAddressDBItem))
				continue;
			PeerAddressDBItem it = (PeerAddressDBItem) item;
			// also add the items to the returned_items list
			if(!AddressUtils.isBogon(it)) {
				resultHandler.accept(match, it);
				returnedItems.add(it);
			}
				
			
		}
		
		if(returnedItems.size() > 0 && firstResultTime == 0)
			firstResultTime = System.currentTimeMillis();

		// if someone has peers he might have filters, collect for scrape
		if (!items.isEmpty() && scrapeHandler != null)
			synchronized (scrapeHandler) {
				scrapeHandler.addGetPeersRespone(gpr);
			}


		// add the peer who responded to the closest nodes list, so we can do an announce
		if (gpr.getToken() != null && !noAnnounce)
			announceCanidates.put(match, gpr.getToken());


		// if we scrape we don't care about tokens.
		// otherwise we're only done if we have found the closest nodes that also returned tokens
		if (noAnnounce || gpr.getToken() != null)
		{
			closest.insert(match);
		}
	}

	/* (non-Javadoc)
	 * @see lbms.plugins.mldht.kad.Task#callTimeout(lbms.plugins.mldht.kad.RPCCall)
	 */
	@Override
	void callTimeout (RPCCall c) {
	}
	
	@Override
	void update () {
		// check if the cache has any closer nodes after the initial query
		if(useCache) {
			Collection<KBucketEntry> cacheResults = cache.get(targetKey, requestConcurrency());
			todo.addCandidates(null, cacheResults);
		}

		for(;;) {
			synchronized (this) {
				RequestPermit p = checkFreeSlot();
				
				if(p == RequestPermit.NONE_ALLOWED)
					break;
				
				KBucketEntry e = todo.next2(kbe -> {
					RequestCandidateEvaluator eval = new RequestCandidateEvaluator(this, closest, todo, kbe, inFlight);
					return eval.goodForRequest(p);
				}).orElse(null);
				
				if(e == null)
					break;
				
				GetPeersRequest gpr = new GetPeersRequest(targetKey);
				// we only request cross-seeding on find-node
				gpr.setWant4(rpc.getDHT().getType() == DHTtype.IPV4_DHT);
				gpr.setWant6(rpc.getDHT().getType() == DHTtype.IPV6_DHT);
				gpr.setDestination(e.getAddress());
				gpr.setScrape(scrapeHandler != null);
				gpr.setNoSeeds(noSeeds);
				
				if(!rpcCall(gpr, e.getID(), call -> {
					if(useCache)
						call.addListener(cache.getRPCListener());
					call.builtFromEntry(e);

					long rtt = e.getRTT();
					long defaultTimeout = rpc.getTimeoutFilter().getStallTimeout();
					
					if(rtt < DHTConstants.RPC_CALL_TIMEOUT_MAX) {
						// the measured RTT is a mean and not the 90th percentile unlike the RPCServer's timeout filter
						// -> add some safety margin to account for variance
						rtt = (long) (rtt * (rtt < defaultTimeout ? 2 : 1.5));
						
						call.setExpectedRTT(min(rtt, DHTConstants.RPC_CALL_TIMEOUT_MAX));
					}
					
					if(DHT.isLogLevelEnabled(LogLevel.Verbose)) {
						List<InetSocketAddress> sources = todo.getSources(e).stream().map(KBucketEntry::getAddress).collect(Collectors.toList());
						DHT.log("Task "+getTaskID()+" sending call to "+ e + " sources:" + sources, LogLevel.Verbose);
					}
						
					
					todo.addCall(call, e);
				})) {
					break;
				}
			}
		}

	}

	
	@Override
	protected boolean isDone() {
		int waitingFor = fastTerminate ? getNumOutstandingRequestsExcludingStalled() : getNumOutstandingRequests();
		
		if(waitingFor > 0)
			return false;
		
		KBucketEntry closest = todo.next().orElse(null);
		
		if (closest == null) {
			return true;
		}
		
				
		RequestCandidateEvaluator eval = new RequestCandidateEvaluator(this, this.closest, todo, closest, inFlight);

		return eval.terminationPrecondition();
	}

	private void updatePopulationEstimator() {

		synchronized (this)
		{
			// feed the estimator if we're sure that we haven't skipped anything in the closest-set
			if(!todo.next().isPresent() && noAnnounce && !fastTerminate && closest.reachedTargetCapacity())
			{
				Set<Key> toEstimate = closest.ids().collect(Collectors.toCollection(HashSet::new));
				rpc.getDHT().getEstimator().update(toEstimate,targetKey);
			}
			
		}
	}

	
	public Map<KBucketEntry, byte[]> getAnnounceCanidates() {
		if(fastTerminate || noAnnounce)
			throw new IllegalStateException("cannot use fast lookups for announces");
		return announceCanidates;
	}


	/**
	 * @return the returned_items
	 */
	public Set<PeerAddressDBItem> getReturnedItems () {
		return Collections.unmodifiableSet(returnedItems);
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
	public void start () {
		//delay the filling of the todo list until we actually start the task
		KClosestNodesSearch kns = new KClosestNodesSearch(targetKey, DHTConstants.MAX_ENTRIES_PER_BUCKET * 4,rpc.getDHT());
		// unlike NodeLookups we do not use unverified nodes here. this avoids rewarding spoofers with useful lookup target IDs
		kns.fill();
		todo.addCandidates(null, kns.getEntries());

		if(useCache) {
			// re-register once we actually started
			cache.register(targetKey,fastTerminate);
			todo.addCandidates(null, cache.get(targetKey,DHTConstants.MAX_CONCURRENT_REQUESTS * 2));
		}
		
		addListener(unused -> {
			logClosest();
		});

		super.start();
	}
}
