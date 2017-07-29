/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import lbms.plugins.mldht.kad.messages.MessageBase;
import lbms.plugins.mldht.kad.messages.MessageBase.Type;

/**
 * A KBucket is just a list of KBucketEntry objects.
 *
 * The list is sorted by time last seen :
 * The first element is the least recently seen, the last
 * the most recently seen.
 *
 * @author Damokles
 */
public class KBucket {

	
	/**
	 * use {@link #insertOrRefresh}, {@link #sortedInsert} or {@link #removeEntry} to handle this<br>
	 * using copy-on-write semantics for this list, referencing it is safe if you make local copy
	 */
	private volatile List<KBucketEntry>	entries;
	
	private AtomicInteger						currentReplacementPointer;
	private AtomicReferenceArray<KBucketEntry>	replacementBucket;
	
	private long						lastRefresh;
	
	public KBucket () {
		entries = new ArrayList<>(); // using arraylist here since reading/iterating is far more common than writing.
		currentReplacementPointer = new AtomicInteger(0);
		replacementBucket = new AtomicReferenceArray<>(DHTConstants.MAX_ENTRIES_PER_BUCKET);
		// needed for bitmasking
		assert(Integer.bitCount(replacementBucket.length()) == 1);
	}

	/**
	 * Notify bucket of new incoming packet from a node, perform update or insert existing nodes where appropriate
	 * @param newEntry The entry to insert
	 */
	public void insertOrRefresh (final KBucketEntry newEntry) {
		if (newEntry == null)
			return;
		
		List<KBucketEntry> entriesRef = entries;
		
		for(KBucketEntry existing : entriesRef) {
			if(existing.equals(newEntry)) {
				existing.mergeInTimestamps(newEntry);
				return;
			}
			
			if(existing.matchIPorID(newEntry)) {
				DHT.logInfo("new node "+newEntry+" claims same ID or IP as "+existing+", might be impersonation attack or IP change. ignoring until old entry times out");
				return;
			}
		}
		
		if(newEntry.verifiedReachable()) {
			if (entriesRef.size() < DHTConstants.MAX_ENTRIES_PER_BUCKET)
			{
				// insert if not already in the list and we still have room
				modifyMainBucket(null,newEntry);
				return;
			}

			if (replaceBadEntry(newEntry))
				return;

			KBucketEntry youngest = entriesRef.get(entriesRef.size()-1);

			// older entries displace younger ones (although that kind of stuff should probably go through #modifyMainBucket directly)
			// entries with a 2.5times lower RTT than the current youngest one displace the youngest. safety factor to prevent fibrilliation due to changing RTT-estimates / to only replace when it's really worth it
			if (youngest.getCreationTime() > newEntry.getCreationTime() || newEntry.getRTT() * 2.5 < youngest.getRTT())
			{
				modifyMainBucket(youngest,newEntry);
				// it was a useful entry, see if we can use it to replace something questionable
				insertInReplacementBucket(youngest);
				return;
			}

		}
		
		insertInReplacementBucket(newEntry);
	}
	
	
	public void refresh(KBucketEntry toRefresh) {
		entries.stream().filter(toRefresh::equals).findAny().ifPresent(e -> {
			e.mergeInTimestamps(toRefresh);
		});
		
		replacementsStream().filter(toRefresh::equals).findAny().ifPresent(e -> {
			e.mergeInTimestamps(toRefresh);
		});
		
	}

	/**
	 * mostly meant for internal use or transfering entries into a new bucket.
	 * to update a bucket properly use {@link #insertOrRefresh(KBucketEntry)}
	 */
	public void modifyMainBucket(KBucketEntry toRemove, KBucketEntry toInsert) {
	
		// we're synchronizing all modifications, therefore we can freely reference the old entry list, it will not be modified concurrently
		synchronized (this)
		{
			if(toInsert != null && entries.stream().anyMatch(toInsert::matchIPorID))
				return;
			
			List<KBucketEntry> newEntries = new ArrayList<>(entries);
			boolean removed = false;
			boolean added = false;
			
			// removal never violates ordering constraint, no checks required
			if(toRemove != null)
				removed = newEntries.remove(toRemove);
			
			

			if(toInsert != null)
			{
				int oldSize = newEntries.size();
				boolean wasFull = oldSize >= DHTConstants.MAX_ENTRIES_PER_BUCKET;
				KBucketEntry youngest = oldSize > 0 ? newEntries.get(oldSize-1) : null;
				boolean unorderedInsert = youngest != null && toInsert.getCreationTime() < youngest.getCreationTime();
				added = !wasFull || unorderedInsert;
				if(added)
				{
					newEntries.add(toInsert);
					removeFromReplacement(toInsert).ifPresent(toInsert::mergeInTimestamps);
				} else
				{
					insertInReplacementBucket(toInsert);
				}
				
				if(unorderedInsert)
					Collections.sort(newEntries,KBucketEntry.AGE_ORDER);
				
				if(wasFull && added)
					while(newEntries.size() > DHTConstants.MAX_ENTRIES_PER_BUCKET)
						insertInReplacementBucket(newEntries.remove(newEntries.size()-1));
			}
			
			// make changes visible
			if(added || removed)
				entries = newEntries;
		}
	}

	/**
	 * Get the number of entries.
	 *
	 * @return The number of entries in this Bucket
	 */
	public int getNumEntries () {
		return entries.size();
	}
	
	public boolean isFull() {
		return entries.size() >= DHTConstants.MAX_ENTRIES_PER_BUCKET;
	}
	
	public int getNumReplacements() {
		int c = 0;
		for(int i=0;i<replacementBucket.length();i++)
			if(replacementBucket.get(i) != null)
				c++;
		return c;
	}

	/**
	 * @return the entries
	 */
	public List<KBucketEntry> getEntries () {
		return new ArrayList<>(entries);
	}
	
	public Stream<KBucketEntry> entriesStream() {
		return entries.stream();
	}
	
	Stream<KBucketEntry> replacementsStream() {
		return IntStream.range(0, replacementBucket.length()).mapToObj(replacementBucket::get).filter(Objects::nonNull);
	}
	
	public List<KBucketEntry> getReplacementEntries() {
		List<KBucketEntry> repEntries = new ArrayList<>(replacementBucket.length());
		int current = currentReplacementPointer.get();
		for(int i=1;i<=replacementBucket.length();i++)
		{
			KBucketEntry e = replacementBucket.get((current + i) % replacementBucket.length());
			if(e != null)
				repEntries.add(e);
		}
		return repEntries;
	}

	/**
	 * A peer failed to respond
	 * @param addr Address of the peer
	 */
	public void onTimeout(InetSocketAddress addr) {
		List<KBucketEntry> entriesRef = entries;
		for (int i = 0, n=entriesRef.size(); i < n; i++)
		{
			KBucketEntry e = entriesRef.get(i);
			if (e.getAddress().equals(addr))
			{
				e.signalRequestTimeout();
				//only removes the entry if it is bad
				removeEntryIfBad(e, false);
				return;
			}
		}
		
		for(int i=0, n=replacementBucket.length();i<n;i++) {
			KBucketEntry e = replacementBucket.get(i);
			if(e != null && e.getAddress().equals(addr)) {
				e.signalRequestTimeout();
				return;
			}
		}
		return;
	}

	/**
	 * Check if the bucket needs to be refreshed
	 *
	 * @return true if it needs to be refreshed
	 */
	public boolean needsToBeRefreshed () {
		long now = System.currentTimeMillis();
		// TODO: timer may be somewhat redundant with needsPing logic
		return now - lastRefresh > DHTConstants.BUCKET_REFRESH_INTERVAL && entries.stream().anyMatch(KBucketEntry::needsPing);
	}
	
	public static final long REPLACEMENT_PING_MIN_INTERVAL = 30*1000;
	
	boolean needsReplacementPing() {
		long now = System.currentTimeMillis();
		
		return now - lastRefresh > REPLACEMENT_PING_MIN_INTERVAL && (entriesStream().anyMatch(KBucketEntry::needsReplacement) || entries.size() == 0) && replacementsStream().anyMatch(KBucketEntry::neverContacted);
	}


	/**
	 * Resets the last modified for this Bucket
	 */
	public void updateRefreshTimer () {
		lastRefresh = System.currentTimeMillis();
	}
	

	@Override
	public String toString() {
		return "entries: "+entries+" replacements: "+replacementBucket;
	}

	/**
	 * Tries to instert entry by replacing a bad entry.
	 *
	 * @param entry Entry to insert
	 * @return true if replace was successful
	 */
	private boolean replaceBadEntry (KBucketEntry entry) {
		List<KBucketEntry> entriesRef = entries;
		for (int i = 0,n=entriesRef.size();i<n;i++) {
			KBucketEntry e = entriesRef.get(i);
			if (e.needsReplacement()) {
				// bad one get rid of it
				modifyMainBucket(e, entry);
				return true;
			}
		}
		return false;
	}
	
	private KBucketEntry pollVerifiedReplacementEntry()
	{
		while(true) {
			int bestIndex = -1;
			KBucketEntry bestFound = null;
			
			for(int i=0;i<replacementBucket.length();i++) {
				KBucketEntry entry = replacementBucket.get(i);
				if(entry == null || !entry.verifiedReachable())
					continue;
				boolean isBetter = bestFound == null || entry.getRTT() < bestFound.getRTT() || (entry.getRTT() == bestFound.getRTT() && entry.getLastSeen() > bestFound.getLastSeen());
				
				if(isBetter) {
					bestFound = entry;
					bestIndex = i;
				}
			}
			
			if(bestFound == null)
				return null;
			
			int newPointer = bestIndex-1;
			if(newPointer < 0)
				newPointer = replacementBucket.length()-1;
			if(replacementBucket.compareAndSet(bestIndex, bestFound, null)) {
				currentReplacementPointer.set(newPointer);
				return bestFound;
			}
		}
	}
	
	private Optional<KBucketEntry> removeFromReplacement(KBucketEntry toRemove) {
		for(int i=0;i<replacementBucket.length();i++) {
			KBucketEntry e = replacementBucket.get(i);
			if(e == null || !e.matchIPorID(toRemove))
				continue;
			replacementBucket.compareAndSet(i, e, null);
			if(e.equals(toRemove))
				return Optional.of(e);
				
		}
		
		return Optional.empty();
		
	}
	
	public Optional<KBucketEntry> findPingableReplacement() {
		for(int i=0; i<replacementBucket.length();i++) {
			KBucketEntry e = replacementBucket.get(i);
			if(e == null || !e.neverContacted())
				continue;
			return Optional.of(e);
		}
		
		return Optional.empty();
	}
	
	void insertInReplacementBucket(KBucketEntry toInsert)
	{
		if(toInsert == null)
			return;
		
		outer:
		while(true)
		{
			int insertationPoint = currentReplacementPointer.incrementAndGet() & (replacementBucket.length() - 1);
			
			KBucketEntry toOverwrite = replacementBucket.get(insertationPoint);
			
			boolean canOverwrite;
			
			if(toOverwrite == null) {
				canOverwrite = true;
			} else {
				int lingerTime = toOverwrite.verifiedReachable() && !toInsert.verifiedReachable() ? 5*60*1000 : 1000;
				canOverwrite = toInsert.getLastSeen() - toOverwrite.getLastSeen() > lingerTime || toInsert.getRTT() < toOverwrite.getRTT();
			}
			
			if(!canOverwrite)
				break;

			for(int i=0;i<replacementBucket.length();i++)
			{
				// don't insert if already present
				KBucketEntry potentialDuplicate = replacementBucket.get(i);
				if(toInsert.matchIPorID(potentialDuplicate)) {
					if(toInsert.equals(potentialDuplicate))
						potentialDuplicate.mergeInTimestamps(toInsert);
					break outer;
				}

			}

			if(replacementBucket.compareAndSet(insertationPoint, toOverwrite, toInsert))
				break;
		}
	}
	
	/**
	 * main list not full or contains entry that needs a replacement -> promote verified entry (if any) from replacement
	 */
	public void promoteVerifiedReplacement() {
		List<KBucketEntry> entriesRef = entries;
		KBucketEntry toRemove = entriesRef.stream().filter(KBucketEntry::needsReplacement).findAny().orElse(null);
		
		if (toRemove == null && entriesRef.size() >= DHTConstants.MAX_ENTRIES_PER_BUCKET)
			return;
		
		KBucketEntry replacement = pollVerifiedReplacementEntry();
		if (replacement != null)
			modifyMainBucket(toRemove, replacement);
	}
	
	public Optional<KBucketEntry> findByIPorID(InetAddress ip, Key id) {
		return entries.stream().filter(e -> e.getID().equals(id) || e.getAddress().getAddress().equals(ip)).findAny();
	}
	
	public Optional<KBucketEntry> randomEntry() {
		return Optional.of(entries).filter(l -> !l.isEmpty()).map(l -> l.get(ThreadLocalRandom.current().nextInt(l.size())));
	}
	
	public void notifyOfResponse(MessageBase msg)
	{
		if(msg.getType() != Type.RSP_MSG || msg.getAssociatedCall() == null)
			return;
		List<KBucketEntry> entriesRef = entries;
		for (int i=0, n = entriesRef.size();i<n;i++)
		{
			KBucketEntry entry = entriesRef.get(i);
			
			// update last responded. insert will be invoked soon, thus we don't have to do the move-to-end stuff
			if(entry.getID().equals(msg.getID()))
			{
				entry.signalResponse(msg.getAssociatedCall().getRTT());
				return;
			}
		}
	}


	/**
	 * @param toRemove Entry to remove, if its bad
	 * @param force if true entry will be removed regardless of its state
	 */
	public void removeEntryIfBad(KBucketEntry toRemove, boolean force) {
		List<KBucketEntry> entriesRef = entries;
		if (entriesRef.contains(toRemove) && (force || toRemove.needsReplacement()))
		{
			KBucketEntry replacement = null;
			replacement = pollVerifiedReplacementEntry();

			// only remove if we have a replacement or really need to
			if(replacement != null || force)
				modifyMainBucket(toRemove,replacement);
		}
		
	}


}
