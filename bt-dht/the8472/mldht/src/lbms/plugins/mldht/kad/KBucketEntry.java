/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad;

import static the8472.bencode.Utils.prettyPrint;
import static the8472.utils.Functional.typedGet;

import lbms.plugins.mldht.kad.utils.AddressUtils;
import lbms.plugins.mldht.utils.ExponentialWeightendMovingAverage;

import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.zip.Checksum;

/**
 * Entry in a KBucket, it basically contains an ip_address of a node,
 * the udp port of the node and a node_id.
 *
 * @author Damokles
 */
public class KBucketEntry {
	
	private static final double RTT_EMA_WEIGHT = 0.3;
	
	
	public static final Class<? extends Checksum> crc32c;
	
	static {
		Class<? extends Checksum> clazz = null;
		
		try {
			clazz = (Class<? extends Checksum>) Class.forName("java.util.zip.CRC32C");
		} catch (ClassNotFoundException e) {
			// not java 9, do nothing
		}
		
		crc32c = clazz;
	}
	
	
	/**
	 * ascending order for last seen, i.e. the last value will be the least recently seen one
	 */
	public static final Comparator<KBucketEntry> LAST_SEEN_ORDER = Comparator.comparingLong(KBucketEntry::getLastSeen);

	/**
	 * ascending order for timeCreated, i.e. the first value will be the oldest
	 */
	public static final Comparator<KBucketEntry> AGE_ORDER = Comparator.comparingLong(KBucketEntry::getCreationTime);

	/**
	 * same order as the Key class, based on the Entrie's nodeID
	 */
	public static final Comparator<KBucketEntry> KEY_ORDER = Comparator.comparing(KBucketEntry::getID);

	
	public static final class DistanceOrder implements Comparator<KBucketEntry> {
		
		final Key target;
		public DistanceOrder(Key target) {
			this.target = target;
		}
	
		public int compare(KBucketEntry o1, KBucketEntry o2) {
			return target.threeWayDistance(o1.getID(), o2.getID());
		}
	}
	

	private final InetSocketAddress	addr;
	private final Key				nodeID;
	private long				lastSeen;
	private boolean verified = false;
	
	/**
	 *   -1 = never queried / learned about it from incoming requests
	 *    0 = last query was a success
	 *  > 0 = query failed
	 */
	private int					failedQueries;
	private long				timeCreated;
	private byte[]				version;
	private ExponentialWeightendMovingAverage avgRTT = new ExponentialWeightendMovingAverage().setWeight(RTT_EMA_WEIGHT);;
	private long lastSendTime = -1;

	
	public static KBucketEntry fromBencoded(Map<String, Object> serialized) {
		
		InetSocketAddress addr = typedGet(serialized, "addr", byte[].class).map(AddressUtils::unpackAddress).orElseThrow(() -> new IllegalArgumentException("address missing"));
		Key id = typedGet(serialized, "id", byte[].class).filter(b -> b.length == Key.SHA1_HASH_LENGTH).map(Key::new).orElseThrow(() -> new IllegalArgumentException("key missing"));
		
		KBucketEntry built = new KBucketEntry(addr, id);
		
		typedGet(serialized, "version", byte[].class).ifPresent(built::setVersion);
		typedGet(serialized, "created", Long.class).ifPresent(l -> built.timeCreated = l);
		typedGet(serialized, "lastSeen", Long.class).ifPresent(l -> built.lastSeen = l);
		typedGet(serialized, "lastSend", Long.class).ifPresent(l -> built.lastSendTime = l);
		typedGet(serialized, "failedCount", Long.class).ifPresent(l -> built.failedQueries = l.intValue());
		typedGet(serialized, "verified", Long.class).ifPresent(l -> built.setVerified(l == 1));
		
		
		return built;
	}
	
	public Map<String, Object> toBencoded() {
		Map<String, Object> map = new TreeMap<>();
		
		map.put("addr", AddressUtils.packAddress(addr));
		map.put("id", nodeID.getHash());
		map.put("created", timeCreated);
		map.put("lastSeen", lastSeen);
		map.put("lastSend", lastSendTime);
		map.put("failedCount", failedQueries());
		if(version != null)
			map.put("version", version);
		if(verifiedReachable())
			map.put("verified", 1);
		
		return map;
	}


	/**
	 * Constructor, set the ip, port and key
	 * @param addr socket address
	 * @param id ID of node
	 */
	public KBucketEntry (InetSocketAddress addr, Key id) {
		Objects.requireNonNull(addr);
		Objects.requireNonNull(id);
		lastSeen = System.currentTimeMillis();
		timeCreated = lastSeen;
		this.addr = addr;
		this.nodeID = id;
	}

	/**
	 * Constructor, set the ip, port and key
	 * @param addr socket address
	 * @param id ID of node
	 * @param timestamp the timestamp when this node last responded
	 */
	public KBucketEntry (InetSocketAddress addr, Key id, long timestamp) {
		Objects.requireNonNull(addr);
		Objects.requireNonNull(id);
		lastSeen = timestamp;
		timeCreated = System.currentTimeMillis();
		this.addr = addr;
		this.nodeID = id;
	}

	/**
	 * Copy constructor.
	 * @param other KBucketEntry to copy
	 * @return
	 */
	public KBucketEntry (KBucketEntry other) {
		addr = other.addr;
		nodeID = other.nodeID;
		lastSeen = other.lastSeen;
		failedQueries = other.failedQueries;
		timeCreated = other.timeCreated;
	}

	/**
	 * @return the address of the node
	 */
	public InetSocketAddress getAddress () {
		return addr;
	}
	
	@Override
	public boolean equals(Object o)
	{
		if(o instanceof KBucketEntry)
			return this.equals((KBucketEntry)o);
		return this == o;
	}

	public boolean equals(KBucketEntry other) {
		if(other == null)
			return false;
		return nodeID.equals(other.nodeID) && addr.equals(other.addr);
	}
	
	public boolean matchIPorID(KBucketEntry other) {
		if(other == null)
			return false;
		return nodeID.equals(other.getID()) || addr.getAddress().equals(other.addr.getAddress());
	}

	@Override
	public int hashCode () {
		return nodeID.hashCode() + 1;
	}

	/**
	 * @return id
	 */
	public Key getID () {
		return nodeID;
	}
	
	/**
     * @param version the version to set
     */
    public void setVersion (byte[] version) {
	    this.version = version;
    }

	/**
     * @return the version
     */
    public Optional<ByteBuffer> getVersion () {
    	return Optional.ofNullable(version).map(ByteBuffer::wrap).map(ByteBuffer::asReadOnlyBuffer);
    }

	/**
	 * @return the last_responded
	 */
	public long getLastSeen () {
		return lastSeen;
	}

	public long getCreationTime() {
		return timeCreated;
	}

	/**
	 * @return the failedQueries
	 */
	public int getFailedQueries () {
		return failedQueries;
	}
	
	@Override
	public String toString() {
		long now = System.currentTimeMillis();
		StringBuilder b = new StringBuilder(80);
		b.append(nodeID+"/"+addr);
		if(lastSendTime > 0)
			b.append(";sent:"+Duration.ofMillis(now-lastSendTime));
		b.append(";seen:"+Duration.ofMillis(now-lastSeen));
		b.append(";age:"+Duration.ofMillis(now-timeCreated));
		if(failedQueries != 0)
			b.append(";fail:"+failedQueries);
		if(verified)
			b.append(";verified");
		double rtt = avgRTT.getAverage();
		if(!Double.isNaN(rtt))
			b.append(";rtt:"+rtt);
		if(version != null)
			b.append(";ver:"+prettyPrint(version));
			
		return b.toString();
	}




	// 5 timeouts, used for exponential backoff as per kademlia paper
	public static final int MAX_TIMEOUTS = 5;
	
	// haven't seen it for a long time + timeout == evict sooner than pure timeout based threshold. e.g. for old entries that we haven't touched for a long time
	public static final int OLD_AND_STALE_TIME = 15*60*1000;
	public static final int PING_BACKOFF_BASE_INTERVAL = 60*1000;
	public static final int OLD_AND_STALE_TIMEOUTS = 2;
	
	public boolean eligibleForNodesList() {
		// 1 timeout can occasionally happen. should be fine to hand it out as long as we've verified it at least once
		return verifiedReachable() && failedQueries < 2;
	}
	
	
	public boolean eligibleForLocalLookup() {
		// allow implicit initial ping during lookups
		// TODO: make this work now that we don't keep unverified entries in the main bucket
		if((!verifiedReachable() && failedQueries > 0) || failedQueries > 3)
			return false;
		return true;
	}
	
	public boolean verifiedReachable() {
		return verified;
	}
	
	public boolean neverContacted() {
		return lastSendTime == -1;
	}
	
	public int failedQueries() {
		return Math.abs(failedQueries);
	}
	
	public long lastSendTime() {
		return lastSendTime;
	}
	
	private boolean withinBackoffWindow(long now) {
		int backoff = PING_BACKOFF_BASE_INTERVAL << Math.min(MAX_TIMEOUTS, Math.max(0, failedQueries() - 1));
		
		return failedQueries != 0 && now - lastSendTime < backoff;
	}
	
	public long backoffWindowEnd() {
		if(failedQueries == 0 || lastSendTime <= 0)
			return -1L;
		
		int backoff = PING_BACKOFF_BASE_INTERVAL << Math.min(MAX_TIMEOUTS, Math.max(0, failedQueries() - 1));
		
		return lastSendTime + backoff;
	}
	
	public boolean withinBackoffWindow() {
		return withinBackoffWindow(System.currentTimeMillis());
	}
	
	public boolean needsPing() {
		long now = System.currentTimeMillis();
		
		// don't ping if recently seen to allow NAT entries to time out
		// see https://arxiv.org/pdf/1605.05606v1.pdf for numbers
		// and do exponential backoff after failures to reduce traffic
		if(now - lastSeen < 30*1000 || withinBackoffWindow(now))
			return false;
		
		return failedQueries != 0 || now - lastSeen > OLD_AND_STALE_TIME;
	}

	// old entries, e.g. from routing table reload
	private boolean oldAndStale() {
		return failedQueries > OLD_AND_STALE_TIMEOUTS && System.currentTimeMillis() - lastSeen > OLD_AND_STALE_TIME;
	}
	
	public boolean removableWithoutReplacement() {
		// some non-reachable nodes may contact us repeatedly, bumping the last seen counter. they might be interesting to keep around so we can keep track of the backoff interval to not waste pings on them
		// but things we haven't heard from in a while can be discarded
		
		boolean seenSinceLastPing = lastSeen > lastSendTime;
		
		return failedQueries > MAX_TIMEOUTS && !seenSinceLastPing ;
	}
	
	public boolean needsReplacement() {
		return (failedQueries > 1 && !verifiedReachable()) || failedQueries > MAX_TIMEOUTS || oldAndStale();
	}

	public void mergeInTimestamps(KBucketEntry other) {
		if(!this.equals(other) || this == other)
			return;
		lastSeen = Math.max(lastSeen, other.getLastSeen());
		lastSendTime = Math.max(lastSendTime, other.lastSendTime);
		timeCreated = Math.min(timeCreated, other.getCreationTime());
		if(other.verifiedReachable())
			setVerified(true);
		if(!Double.isNaN(other.avgRTT.getAverage()) )
			avgRTT.updateAverage(other.avgRTT.getAverage());
	}
	
	public int getRTT() {
		return (int) avgRTT.getAverage(DHTConstants.RPC_CALL_TIMEOUT_MAX);
	}

	/**
	 * 
	 * @param rtt > 0 in ms. -1 if unknown
	 */
	public void signalResponse(long rtt) {
		lastSeen = System.currentTimeMillis();
		failedQueries = 0;
		verified = true;
		if(rtt > 0)
			avgRTT.updateAverage(rtt);
	}
	
	public void mergeRequestTime(long requestSent) {
		lastSendTime = Math.max(lastSendTime, requestSent);
	}
	
	public void signalScheduledRequest() {
		lastSendTime = System.currentTimeMillis();
	}


	/**
	 * Should be called to signal that a request to this peer has timed out;
	 */
	public void signalRequestTimeout () {
		failedQueries++;
	}
	
	
	byte[] v4_mask = { 0x03, 0x0f, 0x3f, (byte) 0xff };
	byte[] v6_mask = { 0x01, 0x03, 0x07, 0x0f, 0x1f, 0x3f, 0x7f, (byte) 0xff };
	
	public boolean hasSecureID() {
		if(crc32c == null)
			return false;
		try {
			Checksum c = crc32c.getConstructor().newInstance();
			
			byte[] ip = getAddress().getAddress().getAddress();
			
			byte[] mask = ip.length == 4 ? v4_mask : v6_mask;
			
			for(int i=0;i<mask.length;i++) {
				ip[i] &= mask[i];
			}
			
			int r = nodeID.getByte(19) & 0x7;
			
			ip[0] |= r << 5;
			
			c.reset();
			c.update(ip, 0, ip.length);
			int crc = (int) c.getValue();
			
			return ((nodeID.getInt(0) ^ crc) & 0xff_ff_f8_00) == 0;
			 
			
			/*
			uint8_t* ip; // our external IPv4 or IPv6 address (network byte order)
			int num_octets; // the number of octets to consider in ip (4 or 8)
			uint8_t node_id[20]; // resulting node ID


			uint8_t* mask = num_octets == 4 ? v4_mask : v6_mask;

			for (int i = 0; i < num_octets; ++i)
			        ip[i] &= mask[i];

			uint32_t rand = std::rand() & 0xff;
			uint8_t r = rand & 0x7;
			ip[0] |= r << 5;

			uint32_t crc = 0;
			crc = crc32c(crc, ip, num_octets);

			// only take the top 21 bits from crc
			node_id[0] = (crc >> 24) & 0xff;
			node_id[1] = (crc >> 16) & 0xff;
			node_id[2] = ((crc >> 8) & 0xf8) | (std::rand() & 0x7);
			for (int i = 3; i < 19; ++i) node_id[i] = std::rand();
			node_id[19] = rand;
			*/
			
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	
	void setVerified(boolean ver) {
		verified = ver;
		
	}
}
