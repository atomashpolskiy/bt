/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad.tasks;

import java.util.Comparator;
import java.util.NavigableSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Stream;

import lbms.plugins.mldht.kad.KBucketEntry;
import lbms.plugins.mldht.kad.Key;

/*
 * We need to detect when the closest set is stable
 *  - in principle we're done as soon as there is no request candidates
 */
public class ClosestSet {
	
	final NavigableSet<KBucketEntry> closest;
	final int targetSize;
	final Key target;
	final Comparator<Key> targetComp;
	
	int insertAttemptsSinceTailModification = 0;
	int insertAttemptsSinceHeadModification = 0;
	
	
	public ClosestSet(Key target, int targetSize) {
		this.target = target;
		targetComp = new Key.DistanceOrder(target);
		closest = new ConcurrentSkipListSet<>(new KBucketEntry.DistanceOrder(target));
		this.targetSize = targetSize;
	}
	
	boolean reachedTargetCapacity() {
		return closest.size() >= targetSize;
	}
	
	void insert(KBucketEntry reply) {
		synchronized (this) {
			closest.add(reply);
			if (closest.size() > targetSize)
			{
				KBucketEntry last = closest.last();
				closest.remove(last);
				if(last == reply)
					insertAttemptsSinceTailModification++;
				else
					insertAttemptsSinceTailModification = 0;
			}
			
			if(closest.first() == reply) {
				insertAttemptsSinceHeadModification = 0;
			} else {
				insertAttemptsSinceHeadModification++;
			}
		}
	}

	
	Stream<Key> ids() {
		return closest.stream().map(KBucketEntry::getID);
	}
	
	Stream<KBucketEntry> entries() {
		return closest.stream();
	}
	
	public Key tail() {
		if(closest.isEmpty())
			return target.distance(Key.MAX_KEY);
			
		return closest.last().getID();
	}
	
	public Key head() {
		if(closest.isEmpty())
			return target.distance(Key.MAX_KEY);
		return closest.first().getID();
	}
	
	@Override
	public String toString() {
		String str = "closestset: " + closest.size() + " tailMod:" + insertAttemptsSinceTailModification +
				" headMod:" + insertAttemptsSinceHeadModification;
		str += " head:" + head().findApproxKeyDistance(target) + " tail:" + tail().findApproxKeyDistance(target);
		
		return  str;
				
	}
	

}
