/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import lbms.plugins.mldht.kad.DHT.DHTtype;
import lbms.plugins.mldht.kad.Node.RoutingTable;
import lbms.plugins.mldht.kad.utils.PackUtil;

/**
 * @author Damokles
 *
 */
public class KClosestNodesSearch {
	private Key							targetKey;
	private List<KBucketEntry>			entries;
	private int							max_entries;
	private DHT							owner;
	private Comparator<KBucketEntry> comp;
	public Predicate<KBucketEntry> filter = KBucketEntry::eligibleForNodesList;

	/**
	 * Constructor sets the key to compare with
	 * @param key The key to compare with
	 * @param max_entries The maximum number of entries can be in the map
	 * @return
	 */
	public KClosestNodesSearch (Key key, int max_entries, DHT owner) {
		this.targetKey = key;
		this.owner = owner;
		this.max_entries = max_entries;
		this.comp = new KBucketEntry.DistanceOrder(key);
		entries = new ArrayList<>(max_entries + DHTConstants.MAX_ENTRIES_PER_BUCKET);
	}

	/**
	 * @return the Target key of the search
	 */
	public Key getSearchTarget () {
		return targetKey;
	}

	/**
	 * @return the number of entries
	 */
	public int getNumEntries () {
		return entries.size();
	}

	public void fill()
	{
		fill(false);
	}
	
/**

consider the following routing table:

0000000...
0000001...
000001...
00001...
0001...
001...
01...
1...

now consider the following target key:

1001101111011100000000011101011001111100001100000010111010111110101000100010101011101001101111010011011110000111010010001100001101011110100000010000011001101000

the first bucket we will want to pick values from is 1...
the second bucket with the next-higher xor distance actually is 0001...

This requires a non-contiguous search



	 * 
	 */

	
	
	private void insertBucket(KBucket bucket) {
		bucket.entriesStream().filter(filter).forEach(entries::add);
	}
	
	private void shave() {
		int overshoot = entries.size() - max_entries;
		
		if(overshoot <= 0)
			return;

		List<KBucketEntry> tail = entries.subList(Math.max(0, entries.size() - DHTConstants.MAX_ENTRIES_PER_BUCKET), entries.size());
		tail.sort(comp);
		entries.subList(entries.size() - overshoot, entries.size()).clear();
	}
	
	public void fill(boolean includeOurself) {
		RoutingTable table = owner.getNode().table();
		
		
		final int initialIdx = table.indexForId(targetKey);
		int currentIdx = initialIdx;
		
		Node.RoutingTableEntry current = table.get(initialIdx);
		
		
		while(true){
			
			// System.out.println(current.prefix);
			
			insertBucket(current.getBucket());
			
			if(entries.size() >= max_entries)
				break;
			
			Prefix bucketPrefix = current.prefix;
			Prefix targetToBucketDistance = new Prefix(targetKey.distance(bucketPrefix), bucketPrefix.depth); // translate into xor distance, trim trailing bits
			Key incrementedDistance = targetToBucketDistance.add(Key.setBit(targetToBucketDistance.depth)); // increment distance by least significant *prefix* bit
			Key nextBucketTarget = targetKey.distance(incrementedDistance); // translate back to natural distance
			
			
			//System.out.println("dist    " + targetToBucketDistance.toBinString());
			//System.out.println("newDist " +  incrementedDistance.toBinString());
			//System.out.println("target  " +  nextBucketTarget.toBinString());
			
			// guess neighbor bucket that might be next in target order
			int dir = Integer.signum(nextBucketTarget.compareTo(current.prefix));
			int idx;
			
			current = null;
			
			idx = currentIdx + dir;
			if(0 <= idx && idx < table.size())
				current = table.get(idx);
			
			// do binary search if guess turned out incorrect
			if(current == null || !current.prefix.isPrefixOf(nextBucketTarget)) {
				idx = table.indexForId(nextBucketTarget);
				current = table.get(idx);
			}
			
			currentIdx = idx;

			// quit if there are insufficient routing table entries to reach the desired size
			if(currentIdx == initialIdx)
				break;
		}
		
		shave();
		
		RPCServer srv = owner.getServerManager().getRandomActiveServer(true);
		
		if(includeOurself && srv != null && srv.getPublicAddress() != null && entries.size() < max_entries)
		{
			InetSocketAddress sockAddr = new InetSocketAddress(srv.getPublicAddress(), srv.getPort());
			entries.add(new KBucketEntry(sockAddr, srv.getDerivedID()));
		}
	}
	


	public boolean isFull () {
		return entries.size() >= max_entries;
	}

	
	/**
	 * Packs the results in a byte array.
	 *
	 * @return the encoded results.
	 */
	public byte[] pack () {
		if(entries.size() == 0)
			return null;
		int entryLength = owner.getType().NODES_ENTRY_LENGTH;
		
		byte[] buffer = new byte[entries.size() * entryLength];
		int max_items = buffer.length / 26;
		int j = 0;

		for (KBucketEntry e : entries) {
			if (j >= max_items) {
				break;
			}
			PackUtil.PackBucketEntry(e, buffer, j * entryLength, owner.getType());
			j++;
		}
		return buffer;
	}
	
	public NodeList asNodeList() {
		return new NodeList() {
			
			@Override
			public int packedSize() {
				return entries.size() * owner.getType().NODES_ENTRY_LENGTH;
			}
			
			@Override
			public Stream<KBucketEntry> entries() {
				return entries.stream();
			}

			@Override
			public AddressType type() {
				return owner.getType() == DHTtype.IPV4_DHT ? AddressType.V4 : AddressType.V6;
			}
		};
	}

	/**
	 * @return a unmodifiable List of the entries
	 */
	public List<KBucketEntry> getEntries () {
		return Collections.unmodifiableList(entries);
	}
}
