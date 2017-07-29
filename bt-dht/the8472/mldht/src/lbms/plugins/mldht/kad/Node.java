/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static lbms.plugins.mldht.kad.Node.InsertOptions.ALWAYS_SPLIT_IF_FULL;
import static lbms.plugins.mldht.kad.Node.InsertOptions.FORCE_INTO_MAIN_BUCKET;
import static lbms.plugins.mldht.kad.Node.InsertOptions.NEVER_SPLIT;
import static lbms.plugins.mldht.kad.Node.InsertOptions.RELAXED_SPLIT;
import static lbms.plugins.mldht.kad.Node.InsertOptions.REMOVE_IF_FULL;
import static the8472.utils.Functional.typedGet;

import the8472.bencode.BEncoder;
import the8472.utils.AnonAllocator;
import the8472.utils.CowSet;
import the8472.utils.Pair;
import the8472.utils.concurrent.SerializedTaskExecutor;
import the8472.utils.io.NetMask;

import lbms.plugins.mldht.DHTConfiguration;
import lbms.plugins.mldht.kad.DHT.LogLevel;
import lbms.plugins.mldht.kad.messages.MessageBase;
import lbms.plugins.mldht.kad.messages.MessageBase.Type;
import lbms.plugins.mldht.kad.tasks.PingRefreshTask;
import lbms.plugins.mldht.kad.tasks.Task;
import lbms.plugins.mldht.kad.utils.AddressUtils;
import lbms.plugins.mldht.kad.utils.ThreadLocalUtils;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;


/**
 * @author Damokles
 *
 */
public class Node {
	/*
	 * Verification Strategy:
	 * 
	 * - trust incoming requests less than responses to outgoing requests
	 * - most outgoing requests will have an expected ID - expected ID may come from external nodes, so don't take it at face value
	 *  - if response does not match expected ID drop the packet for routing table accounting purposes without penalizing any existing routing table entry
	 * - map routing table entries to IP addresses
	 *  - verified responses trump unverified entries
	 *  - lookup all routing table entry for incoming messages based on IP address (not node ID!) and ignore them if ID does not match
	 *  - also ignore if port changed
	 *  - drop, not just ignore, if we are sure that the incoming message is not fake (mtid-verified response)
	 * - allow duplicate addresses for unverified entries
	 *  - scrub later when one becomes verified
	 * - never hand out unverified entries to other nodes
	 * 
	 * other stuff to keep in mind:
	 * 
	 * - non-reachable nodes may spam -> floods replacements -> makes it hard to get proper replacements without active lookups
	 * 
	 */
	
	public static final class RoutingTableEntry implements Comparable<RoutingTableEntry> {
		
		public RoutingTableEntry(Prefix prefix, KBucket bucket, Predicate<Prefix> checkHome) {
			this.prefix = prefix;
			this.bucket = bucket;
			this.homeBucket = checkHome.test(prefix);
		}
		
		public final Prefix prefix;
		final KBucket bucket;
		final boolean homeBucket;
		
		public KBucket getBucket() {
			return bucket;
		}
		
		public int compareTo(RoutingTableEntry o) {
			return prefix.compareTo(o.prefix);
		}
		
		@Override
		public String toString() {
			return prefix.toString() + " " + bucket.toString();
		}
	}
	
	public static final class RoutingTable {
		
		final RoutingTableEntry[] entries;
		final int[] indexCache;
		
		RoutingTable(RoutingTableEntry... entries) {
			this.entries = entries;
			if(entries.length > 64) {
				indexCache = buildCache();
			} else {
				indexCache = new int[] {0, entries.length};
			}
			
		}
		
		public RoutingTable() {
			this(new RoutingTableEntry[] {new RoutingTableEntry(new Prefix(), new KBucket(), (x) -> true)});
		}
		
		int[] buildCache() {
			int[] cache = new int[256];
			
			assert(Integer.bitCount(cache.length) == 1);
			
			int lsb = Integer.bitCount((cache.length/2)-1)-1;
			
			Key increment = Key.setBit(lsb);
			Key trailingBits = new Prefix(Key.MAX_KEY, lsb).distance(Key.MAX_KEY);
			Key currentLower = new Key(new Prefix(Key.MIN_KEY, lsb));
			Key currentUpper = new Prefix(Key.MIN_KEY, lsb).distance(trailingBits);
			
			int innerOffset = 0;
			
			for(int i=0;i<cache.length;i+=2) {
				cache[i+1] = entries.length;
				
				for(int j=innerOffset;j<entries.length;j++) {
					Prefix p = entries[j].prefix;
					
					if(p.compareTo(currentLower) <= 0) {
						innerOffset = cache[i] = max(cache[i],j);
					}
						
					if(p.compareTo(currentUpper) >= 0) {
						cache[i+1] = min(cache[i+1],j);
						break;
					}
						
				}
				
				currentLower = new Key(new Prefix(currentLower.add(increment), lsb));
				currentUpper = currentLower.distance(trailingBits);
			}
			
			// System.out.println(IntStream.of(cache).mapToObj(Integer::toString).collect(Collectors.joining(", ")));
			
			return cache;
		}
		
		public int indexForId(Key id) {
			int mask = indexCache.length/2 - 1;
			int bits = Integer.bitCount(mask);
			
			int cacheIdx = id.getInt(0);
			
			cacheIdx = Integer.rotateLeft(cacheIdx, bits);
			cacheIdx = cacheIdx & mask;
			cacheIdx <<= 1;
			
	        int lowerBound = indexCache[cacheIdx];
	        int upperBound = indexCache[cacheIdx+1];
	        
	        Prefix pivot = null;

	        while(true) {
	            int pivotIdx = (lowerBound + upperBound) >>> 1;
	            pivot = entries[pivotIdx].prefix;
	            
	            if(pivotIdx == lowerBound)
	            	break;

	            if (pivot.compareTo(id) <= 0)
	           		lowerBound = pivotIdx;
	           	else
	           		upperBound = pivotIdx;
	        }
	        
	        assert(pivot != null && pivot.isPrefixOf(id));
	        
           	return lowerBound;
		}

		
		public RoutingTableEntry entryForId(Key id) {
			return entries[indexForId(id)];
		}
		
		public int size() {
			return entries.length;
		}
		
		public RoutingTableEntry get(int idx) {
			return entries[idx];
		}
		
		public List<RoutingTableEntry> list() {
			return Collections.unmodifiableList(Arrays.asList(entries));
		}
		
		public Stream<RoutingTableEntry> stream() {
			return Arrays.stream(entries);
		}
		
		public RoutingTable modify(Collection<RoutingTableEntry> toRemove, Collection<RoutingTableEntry> toAdd) {
			List<RoutingTableEntry> temp = new ArrayList<>(Arrays.asList(entries));
			if(toRemove != null)
				temp.removeAll(toRemove);
			if(toAdd != null)
				temp.addAll(toAdd);
			return new RoutingTable(temp.stream().sorted().toArray(RoutingTableEntry[]::new));
		}
		
	}

	private Object CoWLock = new Object();
	private volatile RoutingTable routingTableCOW = new RoutingTable();
	
	
	
	
	private DHT dht;
	private int num_receives;
	
	private int numReceivesAtLastCheck;
	private long timeOfLastPingCheck;
	private long timeOfLastReceiveCountChange;
	private long timeOfRecovery;
	private int num_entries;
	private Key baseKey;
	private final CowSet<Key> usedIDs = new CowSet<>();
	private volatile Map<InetAddress,RoutingTableEntry> knownNodes = new HashMap<>();
	private ConcurrentHashMap<InetAddress , Long> unsolicitedThrottle = new ConcurrentHashMap<>();
	private Map<KBucket, Task> maintenanceTasks = new IdentityHashMap<>();
	
	Collection<NetMask> trustedNodes = Collections.emptyList();
	
	/**
	 * @param srv
	 */
	public Node(DHT dht) {
		this.dht = dht;
		num_receives = 0;
		num_entries = 0;
	}
	
	void recieved(MessageBase msg) {
		sequentialReceived.accept(msg);
	}
	
	Consumer<MessageBase> sequentialReceived = SerializedTaskExecutor.runSerialized(this::recievedConcurrent);

	/**
	 * An RPC message was received, the node must now update the right bucket.
	 * @param msg The message
	 */
	void recievedConcurrent(MessageBase msg) {
		InetAddress ip = msg.getOrigin().getAddress();
		Key id = msg.getID();
		
		Optional<RPCCall> associatedCall = Optional.ofNullable(msg.getAssociatedCall());
		Optional<Key> expectedId = associatedCall.map(RPCCall::getExpectedID);
		Optional<Pair<KBucket, KBucketEntry>> entryByIp = bucketForIP(ip);
		
		if(entryByIp.isPresent()) {
			KBucket oldBucket = entryByIp.get().a;
			KBucketEntry oldEntry = entryByIp.get().b;
			
			// this might happen if
			// a) multiple nodes on a single IP -> ignore anything but the node we already have in the table
			// b) one node changes ports (broken NAT?) -> ignore until routing table entry times out
			if(oldEntry.getAddress().getPort() != msg.getOrigin().getPort())
				return;
				
			
			if(!oldEntry.getID().equals(id)) { // ID mismatch
				
				if(associatedCall.isPresent()) {
					/*
					 *  we are here because:
					 *  a) a node with that IP is in our routing table
					 *  b) port matches too
					 *  c) the message is a response (mtid-verified)
					 *  d) the ID does not match our routing table entry
					 * 
					 *  That means we are certain that the node either changed its node ID or does some ID-spoofing.
					 *  In either case we don't want it in our routing table
					 */
					
					DHT.logInfo("force-removing routing table entry "+oldEntry+" because ID-change was detected; new ID:" + msg.getID());
					oldBucket.removeEntryIfBad(oldEntry, true);
					
					// might be pollution attack, check other entries in the same bucket too in case random pings can't keep up with scrubbing.
					RPCServer srv = msg.getServer();
					tryPingMaintenance(oldBucket, "checking sibling bucket entries after ID change was detected", srv, (t) -> t.checkGoodEntries(true));
					
					if(oldEntry.verifiedReachable()) {
						// old verified
						// new verified
						// -> probably misbehaving node. don't insert
						return;
					}
					
					/*
					 *  old never verified
					 *  new verified
					 *  -> may allow insert, as if the old one has never been there
					 * 
					 *  but still need to check expected ID match.
					 *  TODO: if this results in an insert then the known nodes list may be stale
					 */
					
				} else {
					
					// new message is *not* a response -> not verified -> fishy due to ID mismatch -> ignore
					return;
				}
				
			}
			
			
		}
		
		KBucket bucketById = routingTableCOW.entryForId(id).bucket;
		Optional<KBucketEntry> entryById = bucketById.findByIPorID(null, id);
		
		// entry is claiming the same ID as entry with different IP in our routing table -> ignore
		if(entryById.isPresent() && !entryById.get().getAddress().getAddress().equals(ip))
			return;
		
		// ID mismatch from call (not the same as ID mismatch from routing table)
		// it's fishy at least. don't insert even if it proves useful during a lookup
		if(!entryById.isPresent() && expectedId.isPresent() && !expectedId.get().equals(id))
			return;

		KBucketEntry newEntry = new KBucketEntry(msg.getOrigin(), id);
		msg.getVersion().ifPresent(newEntry::setVersion);
		
		// throttle the insert-attempts for unsolicited requests, update-only once they exceed the threshold
		// does not apply to responses
		if(!associatedCall.isPresent() && updateAndCheckThrottle(newEntry.getAddress().getAddress())) {
			refreshOnly(newEntry);
			return;
		}
		
		associatedCall.ifPresent(c -> {
			newEntry.signalResponse(c.getRTT());
			newEntry.mergeRequestTime(c.getSentTime());
		});
		

		
		// force trusted entry into the routing table (by splitting if necessary) if it passed all preliminary tests and it's not yet in the table
		// although we can only trust responses, anything else might be spoofed to clobber our routing table
		boolean trustedAndNotPresent = !entryById.isPresent() && msg.getType() == Type.RSP_MSG && trustedNodes.stream().anyMatch(mask -> mask.contains(ip));
		
		Set<InsertOptions> opts = EnumSet.noneOf(InsertOptions.class);
		if(trustedAndNotPresent)
			opts.addAll(EnumSet.of(FORCE_INTO_MAIN_BUCKET, REMOVE_IF_FULL));
		if(msg.getType() == Type.RSP_MSG)
			opts.add(RELAXED_SPLIT);
			
		insertEntry(newEntry, opts);
		
		// we already should have the bucket. might be an old one by now due to splitting
		// but it doesn't matter, we just need to update the entry, which should stay the same object across bucket splits
		if(msg.getType() == Type.RSP_MSG) {
			bucketById.notifyOfResponse(msg);
		}
			
		
		num_receives++;
	}
	
	// -1 token per minute, 60 saturation, 30 threshold
	// if we see more than 1 per minute then it'll take 30 minutes until an unsolicited request can go into a replacement bucket again
	public static final long throttleIncrement = 10;
	public static final long throttleSaturation = 60;
	public static final long throttleThreshold = 30;
	public static final long throttleUpdateIntervalMinutes = 1;
	
	/**
	 * @return true if it should be throttled
	 */
	boolean updateAndCheckThrottle(InetAddress addr) {
		long oldVal = unsolicitedThrottle.merge(addr, throttleIncrement, (k, v) -> Math.min(v + throttleIncrement, throttleSaturation)) - throttleIncrement;
		
		return oldVal > throttleThreshold;
	}
	
	private Optional<Pair<KBucket, KBucketEntry>> bucketForIP(InetAddress addr) {
		return Optional.ofNullable(knownNodes.get(addr)).map(RoutingTableEntry::getBucket).flatMap(bucket -> bucket.findByIPorID(addr, null).map(Pair.of(bucket)));
	}
	
	
	public void insertEntry(KBucketEntry entry, boolean internalInsert) {
		insertEntry(entry, internalInsert ? EnumSet.of(FORCE_INTO_MAIN_BUCKET) : EnumSet.noneOf(InsertOptions.class) );
	}
	
	static enum InsertOptions {
		ALWAYS_SPLIT_IF_FULL,
		NEVER_SPLIT,
		RELAXED_SPLIT,
		REMOVE_IF_FULL,
		FORCE_INTO_MAIN_BUCKET
	}
	
	void refreshOnly(KBucketEntry toRefresh) {
		KBucket bucket = routingTableCOW.entryForId(toRefresh.getID()).getBucket();
		
		bucket.refresh(toRefresh);
	}
	
	
	void insertEntry(KBucketEntry toInsert, Set<InsertOptions> opts) {
		if(usedIDs.contains(toInsert.getID()) || AddressUtils.isBogon(toInsert.getAddress()))
			return;

		if(!dht.getType().canUseSocketAddress(toInsert.getAddress()))
			throw new IllegalArgumentException("attempting to insert "+toInsert+" expected address type: "+dht.getType().PREFERRED_ADDRESS_TYPE.getSimpleName());
		
		
		Key nodeID = toInsert.getID();
		
		RoutingTable currentTable = routingTableCOW;
		RoutingTableEntry tableEntry = currentTable.entryForId(nodeID);

		while(!opts.contains(NEVER_SPLIT) && tableEntry.bucket.isFull() && (opts.contains(FORCE_INTO_MAIN_BUCKET) || toInsert.verifiedReachable()) && tableEntry.prefix.getDepth() < Key.KEY_BITS - 1)
		{
			if(!opts.contains(ALWAYS_SPLIT_IF_FULL) && !canSplit(tableEntry, toInsert, opts.contains(RELAXED_SPLIT)))
				break;
			
			splitEntry(currentTable, tableEntry);
			currentTable = routingTableCOW;
			tableEntry = currentTable.entryForId(nodeID);
		}
		
		int oldSize = tableEntry.bucket.getNumEntries();
		
		KBucketEntry toRemove = null;
		
		if(opts.contains(REMOVE_IF_FULL)) {
			toRemove = tableEntry.bucket.getEntries().stream().filter(e -> trustedNodes.stream().noneMatch(mask -> mask.contains(e.getAddress().getAddress()))).max(KBucketEntry.AGE_ORDER).orElse(null);
		}
		
		if(opts.contains(FORCE_INTO_MAIN_BUCKET))
			tableEntry.bucket.modifyMainBucket(toRemove,toInsert);
		else
			tableEntry.bucket.insertOrRefresh(toInsert);
		
		// add delta to the global counter. inaccurate, but will be rebuilt by the bucket checks
		num_entries += tableEntry.bucket.getNumEntries() - oldSize;
		
	}
	
	boolean canSplit(RoutingTableEntry entry, KBucketEntry toInsert, boolean relaxedSplitting) {
		if(entry.homeBucket)
			return true;
		
		if(!relaxedSplitting)
			return false;
		
		Comparator<Key> comp = new Key.DistanceOrder(toInsert.getID());
		
		Key closestLocalId = usedIDs.stream().min(comp).orElseThrow(() -> new IllegalStateException("expected to find a local ID"));
		
		KClosestNodesSearch search = new KClosestNodesSearch(closestLocalId, DHTConstants.MAX_ENTRIES_PER_BUCKET, dht);
		
		search.filter = x -> true;
		
		search.fill();
		List<KBucketEntry> found = search.getEntries();
		
		if(found.size() < DHTConstants.MAX_ENTRIES_PER_BUCKET)
			return true;
		
		KBucketEntry max = found.get(found.size()-1);
		
		return closestLocalId.threeWayDistance(max.getID(), toInsert.getID()) > 0;
	}
	
	private void splitEntry(RoutingTable expect, RoutingTableEntry entry) {
		synchronized (CoWLock)
		{
			RoutingTable current = routingTableCOW;
			if(current != expect)
				return;
			
			RoutingTableEntry a = new RoutingTableEntry(entry.prefix.splitPrefixBranch(false), new KBucket(), this::isLocalBucket);
			RoutingTableEntry b = new RoutingTableEntry(entry.prefix.splitPrefixBranch(true), new KBucket(), this::isLocalBucket);
			
			RoutingTable newTable = current.modify(Arrays.asList(entry), Arrays.asList(a, b));
			
			routingTableCOW = newTable;
			
			// suppress recursive splitting to relinquish the lock faster. this method is generally called in a loop anyway
			for(KBucketEntry e : entry.bucket.getEntries())
				insertEntry(e, EnumSet.of(InsertOptions.NEVER_SPLIT, InsertOptions.FORCE_INTO_MAIN_BUCKET));
		}
		
		// replacements are less important, transfer outside lock
		for(KBucketEntry e : entry.bucket.getReplacementEntries())
			insertEntry(e, EnumSet.noneOf(InsertOptions.class));
		
	}
	
	public RoutingTable table() {
		return routingTableCOW;
	}
	
	public Stream<Map.Entry<InetAddress, Long>> throttledEntries() {
		return unsolicitedThrottle.entrySet().stream();
	}

	/**
	 * @return OurID
	 */
	public Key getRootID () {
		return baseKey;
	}
	
	public boolean isLocalId(Key id) {
		return usedIDs.contains(id);
	}
	
	public boolean isLocalBucket(Prefix p) {
		return usedIDs.stream().anyMatch(p::isPrefixOf);
	}
	
	public Collection<Key> localIDs() {
		return usedIDs.snapshot();
	}
	
	public DHT getDHT() {
		return dht;
	}

	/**
	 * Increase the failed queries count of the bucket entry we sent the message to
	*/
	void onTimeout (RPCCall call) {
		// don't timeout anything if we don't have a connection
		if(isInSurvivalMode())
			return;
		if(!call.getRequest().getServer().isReachable())
			return;
		
		InetSocketAddress dest = call.getRequest().getDestination();
		
		if(call.getExpectedID() != null)
		{
			routingTableCOW.entryForId(call.getExpectedID()).bucket.onTimeout(dest);
		} else {
			RoutingTableEntry entry = knownNodes.get(dest.getAddress());
			if(entry != null)
				entry.bucket.onTimeout(dest);
		}
	}
	
	

	
	void decayThrottle() {
		unsolicitedThrottle.replaceAll((addr, i) -> {
			return i - 1;
		});
		unsolicitedThrottle.values().removeIf(e -> e <= 0);
		
	}

	public boolean isInSurvivalMode() {
		return dht.getServerManager().getActiveServerCount() == 0;
	}
	
	void removeId(Key k)
	{
		usedIDs.remove(k);
		
		
		if(dht.isRunning()) // don't schedule another task if we're already shutting down
			dht.getScheduler().execute(singleThreadedUpdateHomeBuckets);
	}
	
	void registerServer(RPCServer srv) {
		srv.onEnqueue(this::onOutgoingRequest);
	}
	
	private void onOutgoingRequest(RPCCall c) {
		Key expectedId = c.getExpectedID();
		if(expectedId == null)
			return;
		KBucket bucket = routingTableCOW.entryForId(expectedId).getBucket();
		bucket.findByIPorID(c.getRequest().getDestination().getAddress(), expectedId).ifPresent(entry -> {
			entry.signalScheduledRequest();
		});
		bucket.replacementsStream().filter(r -> r.getAddress().equals(c.getRequest().getDestination())).findAny().ifPresent(KBucketEntry::signalScheduledRequest);
	}
	
	Key registerId()
	{
		int idx = 0;
		Key k = null;
		
		while(true)
		{
			k = getRootID().getDerivedKey(idx);
			if(usedIDs.add(k))
				break;
			idx++;
		}
		
		dht.getScheduler().execute(singleThreadedUpdateHomeBuckets);

		return k;
	}
	
	
	

	/**
	 * Check if a buckets needs to be refreshed, and refresh if necessary.
	 */
	public void doBucketChecks (long now) {
		
		boolean survival = isInSurvivalMode();
		
		// don't spam the checks if we're not receiving anything.
		// we don't want to cause too many stray packets somewhere in a network
		if(survival && now - timeOfLastPingCheck > DHTConstants.BOOTSTRAP_MIN_INTERVAL)
			return;
		timeOfLastPingCheck = now;
		
		mergeBuckets();
		
		int newEntryCount = 0;
		
		for (RoutingTableEntry e : routingTableCOW.entries) {
			KBucket b = e.bucket;

			List<KBucketEntry> entries = b.getEntries();
			
			Set<Key> localIds = usedIDs.snapshot();

			boolean wasFull = b.getNumEntries() >= DHTConstants.MAX_ENTRIES_PER_BUCKET;
			for (KBucketEntry entry : entries)
			{
				// remove really old entries, ourselves and bootstrap nodes if the bucket is full
				if (localIds.contains(entry.getID()) || (wasFull && dht.getBootStrapNodes().contains(entry.getAddress()))) {
					b.removeEntryIfBad(entry, true);
					continue;
				}
				

				// remove duplicate entries, keep the older one
				RoutingTableEntry reverseMapping = knownNodes.get(entry.getAddress().getAddress());
				if(reverseMapping != null && reverseMapping != e) {
					KBucket otherBucket = reverseMapping.getBucket();
					KBucketEntry other = otherBucket.findByIPorID(entry.getAddress().getAddress(), null).orElse(null);
					if(other != null && !other.equals(entry)) {
						if(other.getCreationTime() < entry.getCreationTime()) {
							b.removeEntryIfBad(entry, true);
						} else {
							otherBucket.removeEntryIfBad(other, true);
						}
					}
				}
				
			}
			
			boolean refreshNeeded = b.needsToBeRefreshed();
			boolean replacementNeeded = b.needsReplacementPing();
			if(refreshNeeded || replacementNeeded)
				tryPingMaintenance(b, "Refreshing Bucket #" + e.prefix, null, (task) -> {
					task.probeUnverifiedReplacement(replacementNeeded);
				});
			
			if(!survival)	{
				// only replace 1 bad entry with a replacement bucket entry at a time (per bucket)
				b.promoteVerifiedReplacement();
			}
			
			newEntryCount += e.bucket.getNumEntries();


		}
		
		num_entries = newEntryCount;
		
		rebuildAddressCache();
		decayThrottle();
	}

	void tryPingMaintenance(KBucket b, String reason, RPCServer srv, Consumer<PingRefreshTask> taskConfig) {
		if(srv == null)
			srv = dht.getServerManager().getRandomActiveServer(true);
		
		if(maintenanceTasks.containsKey(b))
			return;
		
		
		if(srv != null) {
			PingRefreshTask prt = new PingRefreshTask(srv, this, null, false);
			
			if(taskConfig != null)
				taskConfig.accept(prt);
			prt.setInfo(reason);
			
			prt.addBucket(b);
			
			if(prt.getTodoCount() > 0 && maintenanceTasks.putIfAbsent(b, prt) == null) {
				prt.addListener(x -> maintenanceTasks.remove(b, prt));
				dht.getTaskManager().addTask(prt);
			}
				
		}
	}
	
	
	void mergeBuckets() {
			
		int i = 0;

		// perform bucket merge operations where possible
		while(true) {
			i++;
			if(i < 1)
				continue;
			
			// fine-grained locking to interfere less with other operations
			synchronized (CoWLock) {
				if(i >= routingTableCOW.size())
					break;

				RoutingTableEntry e1 = routingTableCOW.get(i - 1);
				RoutingTableEntry e2 = routingTableCOW.get(i);

				if (e1.prefix.isSiblingOf(e2.prefix)) {
					int effectiveSize1 = (int) (e1.getBucket().entriesStream().filter(e -> !e.removableWithoutReplacement()).count() + e1.getBucket().replacementsStream().filter(KBucketEntry::eligibleForNodesList).count());
					int effectiveSize2 = (int) (e2.getBucket().entriesStream().filter(e -> !e.removableWithoutReplacement()).count() + e2.getBucket().replacementsStream().filter(KBucketEntry::eligibleForNodesList).count());

					// uplift siblings if the other one is dead
					if (effectiveSize1 == 0 || effectiveSize2 == 0) {
						KBucket toLift = effectiveSize1 == 0 ? e2.getBucket() : e1.getBucket();

						RoutingTable table = routingTableCOW;
						routingTableCOW = table.modify(Arrays.asList(e1, e2), Arrays.asList(new RoutingTableEntry(e2.prefix.getParentPrefix(), toLift, this::isLocalBucket)));
						i -= 2;
						continue;
					}

					// check if the buckets can be merged without losing entries

					if (effectiveSize1 + effectiveSize2 <= DHTConstants.MAX_ENTRIES_PER_BUCKET) {

						RoutingTable table = routingTableCOW;
						routingTableCOW = table.modify(Arrays.asList(e1, e2), Arrays.asList(new RoutingTableEntry(e1.prefix.getParentPrefix(), new KBucket(), this::isLocalBucket)));
						
						// no splitting to avoid fibrillation between merge and split operations

						for (KBucketEntry e : e1.bucket.getEntries())
							insertEntry(e, EnumSet.of(InsertOptions.NEVER_SPLIT, InsertOptions.FORCE_INTO_MAIN_BUCKET));
						for (KBucketEntry e : e2.bucket.getEntries())
							insertEntry(e, EnumSet.of(InsertOptions.NEVER_SPLIT, InsertOptions.FORCE_INTO_MAIN_BUCKET));

						e1.bucket.replacementsStream().forEach(r -> {
							insertEntry(r, EnumSet.of(InsertOptions.NEVER_SPLIT));
						});
						e2.bucket.replacementsStream().forEach(r -> {
							insertEntry(r, EnumSet.of(InsertOptions.NEVER_SPLIT));
						});

						i -= 2;
						continue;
					}
				}
			}

		}
	}
	
	final Runnable singleThreadedUpdateHomeBuckets = SerializedTaskExecutor.onceMore(this::updateHomeBuckets);
	
	void updateHomeBuckets() {
		while(true) {
			RoutingTable t = table();
			List<RoutingTableEntry> changed = new ArrayList<>();
			for(int i=0;i < t.size();i++) {
				RoutingTableEntry e = t.get(i);
				// update home bucket status on local ID change
				if(isLocalBucket(e.prefix) != e.homeBucket)
					changed.add(e);
					
			}
			
			synchronized (CoWLock) {
				if(routingTableCOW != t)
					continue;
				if(changed.isEmpty())
					break;
				routingTableCOW = t.modify(changed, changed.stream().map(e -> new RoutingTableEntry(e.prefix, e.bucket, this::isLocalBucket)).collect(Collectors.toList()));
				break;
			}
		}
		
	}
	
	void rebuildAddressCache() {
		Map<InetAddress, RoutingTableEntry> newKnownMap = new HashMap<>(num_entries);
		RoutingTable table = routingTableCOW;
		for(int i=0,n=table.size();i<n;i++)
		{
			RoutingTableEntry entry = table.get(i);
			Stream<KBucketEntry> entries = entry.bucket.entriesStream();
			entries.forEach(e -> {
				newKnownMap.put(e.getAddress().getAddress(), entry);
			});
		}
		
		knownNodes = newKnownMap;
	}

	/**
	 * Check if a buckets needs to be refreshed, and refresh if necesarry
	 *
	 * @param dh_table
	 */
	public void fillBuckets () {
		RoutingTable table = routingTableCOW;

		for (int i = 0;i<table.size();i++) {
			RoutingTableEntry entry = table.get(i);
			
			int num = entry.bucket.getNumEntries();

			// just try to fill partially populated buckets
			// not empty ones, they may arise as artifacts from deep splitting
			if (num > 0 && num < DHTConstants.MAX_ENTRIES_PER_BUCKET) {

				dht.fillBucket(entry.prefix.createRandomKeyFromPrefix(), entry.bucket, t -> {
					t.setInfo("Filling Bucket #" + entry.prefix);
				});
			}
		}
	}

	/**
	 * Saves the routing table to a file
	 *
	 * @param file to save to
	 * @throws IOException
	 */
	void saveTable(Path saveTo) throws IOException {
		// don't persist in test mode
		if(!Files.isDirectory(saveTo.getParent()))
			return;
		
		ByteBuffer tableBuffer = AnonAllocator.allocate(50*1024*1024);
		
		
		Map<String,Object> tableMap = new TreeMap<>();

		RoutingTable table = routingTableCOW;
		
		Stream<Map<String, Object>> main = table.stream().map(RoutingTableEntry::getBucket).flatMap(b -> b.entriesStream().map(KBucketEntry::toBencoded));
		Stream<Map<String, Object>> replacements = table.stream().map(RoutingTableEntry::getBucket).flatMap(b -> b.replacementsStream().map(KBucketEntry::toBencoded));
		
		tableMap.put("mainEntries", main);
		tableMap.put("replacements", replacements);
		
		ByteBuffer doubleBuf = ByteBuffer.wrap(new byte[8]);
		doubleBuf.putDouble(0, dht.getEstimator().getRawDistanceEstimate());
		tableMap.put("log2estimate", doubleBuf);
		
		tableMap.put("timestamp", System.currentTimeMillis());
		tableMap.put("oldKey", getRootID().getHash());
		
		new BEncoder().encodeInto(tableMap, tableBuffer);
		
		Path tempFile = Files.createTempFile(saveTo.getParent(), "saveTable", "tmp");
		
		try(SeekableByteChannel chan = Files.newByteChannel(tempFile, StandardOpenOption.WRITE)) {
			chan.write(tableBuffer);
			chan.close();
			Files.move(tempFile, saveTo, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		};

	}
	
	void initKey(DHTConfiguration config)
	{
		if(config != null && config.isPersistingID()) {
			Path keyPath = config.getStoragePath().resolve("baseID.config");
			File keyFile = keyPath.toFile();
			
			if (keyFile.exists() && keyFile.isFile()) {
				try {
					List<String> raw = Files.readAllLines(keyPath);
					baseKey = raw.stream().map(String::trim).filter(Key.STRING_PATTERN.asPredicate()).findAny().map(Key::new).orElseThrow(() -> new IllegalArgumentException(keyPath.toString()+" did not contain valid node ID"));
					return;
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		
		baseKey = Key.createRandomKey();
		
		persistKey();
	}
	
	void persistKey() {
		DHTConfiguration config = dht.getConfig();
		
		if(config == null)
			return;
		
		Path keyPath = config.getStoragePath().resolve("baseID.config");
		
		try {
			if(!Files.isDirectory(config.getStoragePath()))
				return;
			Path tmpFile = Files.createTempFile(config.getStoragePath(), "baseID", ".tmp");
			
			Files.write(tmpFile, Collections.singleton(baseKey.toString(false)), StandardCharsets.ISO_8859_1);
			Files.move(tmpFile, keyPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		
		
		
	}

	/**
	 * Loads the routing table from a file
	 *
	 * @param file
	 * @param runWhenLoaded is executed when all load operations are finished
	 * @throws IOException
	 */
	void loadTable (Path tablePath) {
		
		File f = tablePath.toFile();
		
		if(!f.exists() || !f.isFile())
			return;
		
		try(FileChannel chan = FileChannel.open(tablePath, StandardOpenOption.READ)) {
			
			// don't use mmap, that would keep the file undeletable on windows, which would interfere with write-atomicmove persistence
			ByteBuffer buf = ByteBuffer.allocateDirect((int)chan.size());
			chan.read(buf);
			buf.flip();
			
			Map<String, Object> table = ThreadLocalUtils.getDecoder().decode(buf);
			
			AtomicInteger counter = new AtomicInteger();

			Key oldKey = typedGet(table, "oldKey", byte[].class).filter(b -> b.length == Key.SHA1_HASH_LENGTH).map(Key::new).orElse(null);
			
			boolean reuseKey = getRootID().equals(oldKey);
			Comparator<KBucketEntry> comp = new KBucketEntry.DistanceOrder(getRootID());
			
			typedGet(table, "mainEntries", List.class).ifPresent(l -> {
				Stream<KBucketEntry> st = l.stream().filter(Map.class::isInstance).map(m -> KBucketEntry.fromBencoded((Map<String, Object>) m));
				if(!reuseKey) // sort so we insert in new home bucket first to minimize reshuffling
					st = st.sorted(comp);
				st.forEachOrdered(be -> {
					insertEntry(be, reuseKey ? EnumSet.of(ALWAYS_SPLIT_IF_FULL, FORCE_INTO_MAIN_BUCKET) : EnumSet.noneOf(InsertOptions.class));
					counter.incrementAndGet();
				});
			});
			
			typedGet(table, "replacements", List.class).ifPresent(l -> {
				Stream<KBucketEntry> st = l.stream().filter(Map.class::isInstance).map(m -> KBucketEntry.fromBencoded((Map<String, Object>) m));
				st.filter(e -> dht.getType().canUseSocketAddress(e.getAddress())).forEach(be -> {
					routingTableCOW.entryForId(be.getID()).bucket.insertInReplacementBucket(be);
					counter.incrementAndGet();
				});
			});
			
			typedGet(table, "log2estimate", byte[].class).filter(b -> b.length == 8).ifPresent(b -> {
				ByteBuffer doubleBuf = ByteBuffer.wrap(b);
				dht.getEstimator().setInitialRawDistanceEstimate(doubleBuf.getDouble());
			});
			

			long timeStamp = typedGet(table, "timestamp", Long.class).orElse(-1L);

			
			DHT.logInfo("Loaded " + counter.get() + " entries from cache. Cache was "
					+ ((System.currentTimeMillis() - timeStamp) / (60 * 1000))
					+ "min old. Reusing old id = " + reuseKey);

			
			rebuildAddressCache();
		} catch (IOException e) {
			DHT.log(e, LogLevel.Error);
		};
		
	}

	/**
	 * Get the number of entries in the routing table
	 *
	 * @return
	 */
	public int getNumEntriesInRoutingTable () {
		return num_entries;
	}
	
	public void setTrustedNetMasks(Collection<NetMask> masks) {
		trustedNodes = masks;
	}
	
	public Collection<NetMask> getTrustedNetMasks() {
		return trustedNodes;
	}
	
	public Optional<KBucketEntry> getRandomEntry() {
		RoutingTable table = routingTableCOW;
		
		int offset = ThreadLocalRandom.current().nextInt(table.size());
		
		// sweep from a random offset in case there are empty buckets
		return IntStream.range(0, table.size()).mapToObj(i -> table.get((i + offset) % table.size()).getBucket().randomEntry()).filter(Optional::isPresent).map(Optional::get).findAny();
	}
	
	@Override
	public String toString() {
		StringBuilder b = new StringBuilder(10000);

		try {
			buildDiagnistics(b);
		} catch (IOException e) {
			throw new Error("should not happen");
		}
			
		return b.toString();
	}
	
	public void buildDiagnistics(Appendable b) throws IOException {
		RoutingTable table = routingTableCOW;
		
		Collection<Key> localIds = localIDs();
		
		b.append("buckets: ");
		b.append(String.valueOf(table.size()));
		b.append(" / entries: ");
		b.append(String.valueOf(num_entries));
		b.append('\n');
		for(RoutingTableEntry e : table.entries) {
			b.append(e.prefix.toString());
			b.append("   num:");
			b.append(String.valueOf(e.bucket.getNumEntries()));
			b.append(" rep:");
			b.append(String.valueOf(e.bucket.getNumReplacements()));
			if(localIds.stream().anyMatch(e.prefix::isPrefixOf))
				b.append(" [Home]");
			b.append('\n');
		}
	}

}
