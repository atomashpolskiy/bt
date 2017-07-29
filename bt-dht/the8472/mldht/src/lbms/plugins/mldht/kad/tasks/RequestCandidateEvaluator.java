/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad.tasks;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Stream;

import lbms.plugins.mldht.kad.KBucketEntry;
import lbms.plugins.mldht.kad.Key;
import lbms.plugins.mldht.kad.RPCCall;
import lbms.plugins.mldht.kad.RPCState;
import lbms.plugins.mldht.kad.tasks.Task.RequestPermit;

/* algo:
 * 1. check termination condition
 * 2. allow if free slot
 * 3. if stall slot check
 * a) is candidate better than non-stalled in flight
 * b) is candidate better than head (homing phase)
 * c) is candidate better than tail (stabilizing phase)
 */

public class RequestCandidateEvaluator {
	
	final Task t;
	final Key target;
	final ClosestSet closest;
	final KBucketEntry candidate;
	final Collection<RPCCall> inFlight;
	final IterativeLookupCandidates todo;
	
	public RequestCandidateEvaluator(TargetedTask t, ClosestSet c, IterativeLookupCandidates todo, KBucketEntry cand, Collection<RPCCall> inFlight) {
		Objects.requireNonNull(cand);
		this.t = t;
		this.todo = todo;
		this.target = t.getTargetKey();
		this.closest = c;
		this.candidate = cand;
		this.inFlight = inFlight;
	}
	
	private Stream<Key> activeInFlight() {
		return inFlight.stream().filter(c -> {
			RPCState state = c.state();
			return state == RPCState.UNSENT || state == RPCState.SENT;
		}).map(RPCCall::getExpectedID);
	}
	
	private boolean inStabilization() {
		int[] suggestedCounts = closest.entries().mapToInt((k) -> {return todo.nodeForEntry(k).sources.size();}).toArray();
		
		return Arrays.stream(suggestedCounts).anyMatch(i -> i >= 5) || Arrays.stream(suggestedCounts).filter(i -> i >= 4).count() >= 2;
	}
	
	public boolean candidateAheadOfClosestSet() {
		return !closest.reachedTargetCapacity() || target.threeWayDistance(closest.head(), candidate.getID()) > 0;
	}
	
	public boolean candidateAheadOfClosestSetTail() {
		return !closest.reachedTargetCapacity() || target.threeWayDistance(closest.tail(), candidate.getID()) > 0;
	}
	
	public int activeInFlightBetterThanCandidate() {
		return (int) activeInFlight().filter(k -> target.threeWayDistance(k, candidate.getID()) < 0).count();
	}
	
	public boolean terminationPrecondition() {
		return !candidateAheadOfClosestSetTail() && (inStabilization() || closest.insertAttemptsSinceTailModification > closest.targetSize);
	}
	
	@Override
	public String toString() {
		return t.age().toMillis() +" "+ t.counts + " " + closest + " cand:" + candidate.getID().findApproxKeyDistance(target);
	}
	
	
	public boolean goodForRequest(RequestPermit p) {
		if(p == RequestPermit.NONE_ALLOWED)
			return false;
		
		boolean result = false;
		
		if(candidateAheadOfClosestSet())
			result = true;

		
		if(candidateAheadOfClosestSetTail() && inStabilization())
			result = true;
		if(!terminationPrecondition() && activeInFlight().count() == 0)
			result = true;
		
		//if(result)
		//	System.out.println("\n" + t.age()  + " " + t.counts+ "\n"+((IteratingTask)t).closestDebug());
		
		return result;

	}
	
	
	

}
