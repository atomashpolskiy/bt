/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lbms.plugins.mldht.kad.DHT.DHTtype;
import lbms.plugins.mldht.kad.utils.ByteWrapper;
import lbms.plugins.mldht.kad.utils.ThreadLocalUtils;

/**
 * @author Damokles
 * 
 */
public class Database {
	private ConcurrentMap<Key, PeersSeeds>	items;
	private AtomicLong timestampCurrent = new AtomicLong();
	private volatile long timestampPrevious;
	private volatile byte[] samples = new byte[0];
	
	public static final int MAX_SAMPLE_COUNT = 20;
	
	private static byte[] sessionSecret = new byte[20];
	
	static {
		ThreadLocalUtils.getThreadLocalRandom().nextBytes(sessionSecret);
	}

	Database() {
		items = new ConcurrentHashMap<>(3000);
	}
	
	
	public static class PeersSeeds {
		ItemSet seeds;
		ItemSet peers;
		
		PeersSeeds(PeerAddressDBItem[] seeds, PeerAddressDBItem[] peers) {
			this.seeds = new ItemSet(seeds);
			this.peers = new ItemSet(peers);
		}
		
		
		boolean add(PeerAddressDBItem it) {
			ItemSet removeTarget = it.seed ? peers : seeds;
			ItemSet insertTarget = it.seed ? seeds : peers;
			
			removeTarget.remove(it);
			return insertTarget.add(it);
		}
		
		void expire() {
			seeds.expire();
			peers.expire();
		}
		
		public ItemSet peers() {
			return peers;
		}
		
		public ItemSet seeds() {
			return seeds;
		}
		
		public int size() {
			return peers.size() + seeds.size();
		}
	}

	
	public static class ItemSet {
		static final PeerAddressDBItem[] NO_ITEMS = new PeerAddressDBItem[0];
		
		
		
		private volatile PeerAddressDBItem[] items = NO_ITEMS;
		private volatile BloomFilterBEP33 filter = null;
		
		ItemSet(PeerAddressDBItem[] initial) {
			this.items = initial;
		}
		
		private void remove(PeerAddressDBItem it) {
			synchronized (this) {
				PeerAddressDBItem[] current = items;
				
				if(current.length == 0)
					return;
				
				
				
				int idx = Arrays.asList(current).indexOf(it);
				if(idx < 0) {
					return;
				}
				
				PeerAddressDBItem[] newItems = Arrays.copyOf(current, current.length - 1);
				
				System.arraycopy(current, idx+1, newItems, idx, newItems.length - idx);
				
				items = newItems;
				invalidateFilters();
			}
		}
		
		/**
		 * @return true when inserting, false when replacing
		 */
		private boolean add(PeerAddressDBItem toAdd) {
			synchronized (this) {
				PeerAddressDBItem[] current = items;
				int idx = Arrays.asList(current).indexOf(toAdd);
				if(idx >= 0) {
					current[idx] = toAdd;
					return false;
				}
				
				PeerAddressDBItem[] newItems = Arrays.copyOf(current, current.length + 1);
				newItems[newItems.length-1] = toAdd;
				Collections.shuffle(Arrays.asList(newItems));

				items = newItems;
				
				// bloom filter supports adding, only deletions need a rebuild.
				BloomFilterBEP33 currentFilter = filter;
				if(currentFilter != null)
					synchronized (currentFilter) {
						currentFilter.insert(toAdd.getInetAddress());
					}
				
				
				return true;
			}
		}
		
		PeerAddressDBItem[] snapshot() {
			return items;
		}
		
		boolean isEmpty() {
			return items.length == 0;
		}
		
		public int size() {
			return items.length;
		}
		
		public Stream<PeerAddressDBItem> stream() {
			return Arrays.stream(items);
		}
		
		private void invalidateFilters() {
			filter = null;
		}
		
		BloomFilterBEP33 getFilter() {
			BloomFilterBEP33 f = filter;
			if(f == null) {
				f = filter = buildFilter();
			}
			
			return f;
		}
		
		private BloomFilterBEP33 buildFilter() {
			// also return empty filters. strict interpretation of the spec doesn't allow omission of empty sets
			// can happen if we have seeds but no peeds for example
			
			BloomFilterBEP33 filter = new BloomFilterBEP33();

			for (PeerAddressDBItem item : items) {
				filter.insert(item.getInetAddress());
			}
			
			return filter;
		}
		
		void expire() {
			synchronized (this) {
				long now = System.currentTimeMillis();
				
				PeerAddressDBItem[] items = this.items;
				PeerAddressDBItem[] newItems = new PeerAddressDBItem[items.length];
				
				// don't remove all at once -> smears out new registrations on popular keys over time
				int toRemove = DHTConstants.MAX_DB_ENTRIES_PER_KEY / 5;
				
				int insertPoint = 0;
				
				for(int i=0;i<items.length;i++) {
					PeerAddressDBItem e = items[i];
					if(toRemove == 0 || !e.expired(now))
						newItems[insertPoint++] = e;
					else
						toRemove--;
				}
				
				if(insertPoint != newItems.length) {
					this.items = Arrays.copyOf(newItems, insertPoint);
					invalidateFilters();
				}
				
			}
			
		}
	}

	/**
	 * Store an entry in the database
	 * 
	 * @param key
	 *            The key
	 * @param dbi
	 *            The DBItem to store
	 */
	public void store(Key key, PeerAddressDBItem dbi) {
		
		
		PeersSeeds keyEntries = null;
		
		items.compute(key, (k, v) -> {
			if(v != null) {
				v.add(dbi);
				return v;
			}
			
			return new PeersSeeds(dbi.seed ? new PeerAddressDBItem[] {dbi} : ItemSet.NO_ITEMS , dbi.seed ? ItemSet.NO_ITEMS : new PeerAddressDBItem[] {dbi});
		});
	}

	/**
	 * Get max_entries items from the database, which have the same key, items
	 * are taken randomly from the list. If the key is not present no items will
	 * be returned, if there are fewer then max_entries items for the key, all
	 * entries will be returned
	 * 
	 * @param key
	 *            The key to search for
	 * @param dbl
	 *            The list to store the items in
	 * @param max_entries
	 *            The maximum number entries
	 */
	List<DBItem> sample(Key key, int max_entries, DHTtype forType, boolean preferPeers) {
		PeersSeeds keyEntry = null;
		PeerAddressDBItem[] seedSnapshot = null;
		PeerAddressDBItem[] peerSnapshot = null;


		keyEntry = items.get(key);
		if(keyEntry == null)
			return null;
		
		seedSnapshot = keyEntry.seeds.snapshot();
		peerSnapshot = keyEntry.peers.snapshot();
		
		int lengthSum = peerSnapshot.length + seedSnapshot.length;
		
		if(lengthSum == 0)
			return null;
		
		List<DBItem> peerlist = new ArrayList<>(Math.min(max_entries, lengthSum));
		
		preferPeers &= lengthSum > max_entries;
		
		PeerAddressDBItem[] source;
		
		if(preferPeers)
			source = peerSnapshot;
		else {
			// proportional sampling
			source = ThreadLocalRandom.current().nextInt(lengthSum) < peerSnapshot.length ? peerSnapshot : seedSnapshot;
		}
		
		fill(peerlist, source, max_entries);
		
		source = source == peerSnapshot ? seedSnapshot : peerSnapshot;
		
		fill(peerlist, source, max_entries);
		
		return peerlist;
	}
	
	
	static void fill(List<DBItem> target, PeerAddressDBItem[] source, int max) {
		if(source.length == 0)
			return;
		
		if(source.length < max - target.size()) {
			// copy whole
			for(int i=0;i<source.length;i++) {
				target.add(source[i]);
			}
		} else {
			// sample random sublist
			int offset = ThreadLocalRandom.current().nextInt(source.length);
			
			for(int i=0;i<source.length && target.size() < max;i++) {
				PeerAddressDBItem toInsert = source[(i+offset)%source.length];
				target.add(toInsert);
			}
			
		}
		
		
	}
	
	BloomFilterBEP33 createScrapeFilter(Key key, boolean seedFilter)
	{
		PeersSeeds dbl = items.get(key);
		
		if (dbl == null)
			return null;
		
		return seedFilter ? dbl.seeds.getFilter() : dbl.peers.getFilter();
	}

	/**
	 * Expire all items older than 30 minutes
	 * 
	 * @param now
	 *            The time it is now (we pass this along so we only have to
	 *            calculate it once)
	 */
	void expire(long now) {
		
		for (PeersSeeds dbl : items.values())
		{
			dbl.expire();
		}
		
		items.entrySet().removeIf(e -> e.getValue().size() == 0);
		
		samples = null;
		
	}
	
	ByteBuffer samples() {
		byte[] currentSamples = samples;
		
		if(currentSamples != null)
			return ByteBuffer.wrap(currentSamples);
		
		List<Key> fullSet = items.keySet().stream().collect(Collectors.toCollection(ArrayList::new));
		
		Collections.shuffle(fullSet);
		
		int size = Math.min(MAX_SAMPLE_COUNT, fullSet.size());
		
		byte[] newSamples = new byte[size * 20];
		
		ByteBuffer buf = ByteBuffer.wrap(newSamples);
		
		fullSet.stream().limit(size).forEach(k -> {
			k.toBuffer(buf);
		});
		buf.flip();

		if(size >= MAX_SAMPLE_COUNT)
			samples = newSamples;
		
		return buf;
	}
	
	
	boolean insertForKeyAllowed(Key target)
	{
		PeersSeeds entries = items.get(target);
		if(entries == null)
			return true;
		
		int size = Math.max(entries.peers.size(), entries.seeds.size());

		if(size < DHTConstants.MAX_DB_ENTRIES_PER_KEY / 5)
			return true;
		
		// TODO: send a token if the node requesting it is already in the DB
		
		if(size >= DHTConstants.MAX_DB_ENTRIES_PER_KEY)
			return false;
		

		
		// implement RED to throttle write attempts
		return size < ThreadLocalRandom.current().nextInt(DHTConstants.MAX_DB_ENTRIES_PER_KEY);
	}
	
	/**
	 * Generate a write token, which will give peers write access to the DB.
	 * 
	 * @param ip
	 *            The IP of the peer
	 * @param port
	 *            The port of the peer
	 * @return A Key
	 */
	ByteWrapper genToken(Key nodeId, InetAddress ip, int port, Key lookupKey) {
		updateTokenTimestamps();
		
		byte[] tdata = new byte[Key.SHA1_HASH_LENGTH + ip.getAddress().length + 2 + 8 + Key.SHA1_HASH_LENGTH + sessionSecret.length];
		// generate a hash of the ip port and the current time
		// should prevent anybody from crapping things up
		ByteBuffer bb = ByteBuffer.wrap(tdata);
		nodeId.toBuffer(bb);
		bb.put(ip.getAddress());
		bb.putShort((short) port);
		bb.putLong(timestampCurrent.get());
		lookupKey.toBuffer(bb);
		bb.put(sessionSecret);
		
		// shorten 4bytes to not waste packet size
		// the chance of guessing correctly would be 1 : 4 million and only be valid for a single infohash
		byte[] token = Arrays.copyOf(ThreadLocalUtils.getThreadLocalSHA1().digest(tdata), 4);
		
		
		return new ByteWrapper(token);
	}
	
	private void updateTokenTimestamps() {
		long current = timestampCurrent.get();
		long now = System.nanoTime();
		while(TimeUnit.NANOSECONDS.toMillis(now - current) > DHTConstants.TOKEN_TIMEOUT)
		{
			if(timestampCurrent.compareAndSet(current, now))
			{
				timestampPrevious = current;
				break;
			}
			current = timestampCurrent.get();
		}
	}

	/**
	 * Check if a received token is OK.
	 * 
	 * @param token
	 *            The token received
	 * @param ip
	 *            The ip of the sender
	 * @param port
	 *            The port of the sender
	 * @return true if the token was given to this peer, false other wise
	 */
	boolean checkToken(ByteWrapper token, Key nodeId, InetAddress ip, int port, Key lookupKey) {
		updateTokenTimestamps();
		boolean valid = checkToken(token, nodeId, ip, port, lookupKey, timestampCurrent.get()) || checkToken(token, nodeId, ip, port, lookupKey, timestampPrevious);
		if(!valid)
			DHT.logDebug("Received Invalid token from " + ip.getHostAddress());
		return valid;
	}


	private boolean checkToken(ByteWrapper toCheck, Key nodeId, InetAddress ip, int port, Key lookupKey, long timeStamp) {

		byte[] tdata = new byte[Key.SHA1_HASH_LENGTH + ip.getAddress().length + 2 + 8 + Key.SHA1_HASH_LENGTH + sessionSecret.length];
		ByteBuffer bb = ByteBuffer.wrap(tdata);
		nodeId.toBuffer(bb);
		bb.put(ip.getAddress());
		bb.putShort((short) port);
		bb.putLong(timeStamp);
		bb.put(lookupKey.getHash());
		bb.put(sessionSecret);
		
		byte[] rawToken = Arrays.copyOf(ThreadLocalUtils.getThreadLocalSHA1().digest(tdata), 4);
		
		return toCheck.equals(new ByteWrapper(rawToken));
	}
	
	public Map<Key, PeersSeeds> getData() {
		return new HashMap<>(items);
	}


	/**
	 * @return the stats
	 */
	public DatabaseStats getStats() {
		
		return new DatabaseStats() {
			
			
			@Override
			public int getKeyCount() {
				// TODO Auto-generated method stub
				return items.size();
			}
			
			@Override
			public int getItemCount() {
				return items.values().stream().mapToInt(PeersSeeds::size).sum();
			}
		};
	}
}
