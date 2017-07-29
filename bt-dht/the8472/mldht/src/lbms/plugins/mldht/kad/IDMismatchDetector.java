/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad;

import lbms.plugins.mldht.kad.messages.FindNodeRequest;
import lbms.plugins.mldht.kad.messages.GetPeersRequest;
import lbms.plugins.mldht.kad.messages.MessageBase;
import lbms.plugins.mldht.kad.messages.PingRequest;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Two-step detection.
 * 
 * 1. we store responses that deviated form their expected ID. we can't ban for this since the expectation (supplied by external nodes) can be incorrect. but we can use it to fix up future expectations
 * 2. we do an active lookup or wait for a second call to complete to see whether the node really changes ID
 */
public class IDMismatchDetector {
	
	public static final long OBSERVATION_PERIOD = TimeUnit.MINUTES.toMillis(15);
	public static final long ACTIVE_ID_CHANGE_BAN_DURATION = TimeUnit.HOURS.toMillis(12);
	// passive observations happen over larger time windows, so they might catch some "normal" ID changes (node restarts). swing a lighter ban-hammer for those cases
	public static final long PASSIVE_ID_CHANGE_BAN_DURATION = TimeUnit.MINUTES.toMillis(40);
	public static final long ACTIVE_CHECK_BACKOFF_INTERVAL = TimeUnit.MINUTES.toMillis(25);
	
	
	enum State {
		CONFIRMED_INCONSISTENT_ID,
		OBSERVING_PASSIVELY
	}
	
	final DHT dht;
	
	static class ObservationEntry {
		long expirationTime;
		long lastActiveCheck;
		State state;
		Key lastObservedId;
	}
	
	public IDMismatchDetector(DHT owner) {
		dht = owner;
	}
	
	Map<InetSocketAddress, ObservationEntry> underObservation = new ConcurrentHashMap<>();
	Map<InetAddress, Long> merged = new ConcurrentHashMap();
	
	Map<RPCServer, RPCCall> activeLookups = new ConcurrentHashMap<>();
	
	void add(RPCCall toInspect) {
		if(toInspect.state() != RPCState.RESPONDED)
			return;
		
		updateExisting(toInspect);
		
		if(toInspect.getExpectedID() == null)
			return;
		
		if(!toInspect.matchesExpectedID()) {
			passiveObservation(toInspect);
			activeCheck(toInspect);
		}
		
		
	}
	
	void updateExisting(RPCCall newCall) {
		underObservation.computeIfPresent(newCall.getRequest().getDestination(), (addr, obs ) -> {
			Key newId = newCall.getResponse().getID();
			
			if(obs.state == State.OBSERVING_PASSIVELY && !obs.lastObservedId.equals(newId)) {
				ObservationEntry newObservation = new ObservationEntry();
				newObservation.lastObservedId = newId;
				newObservation.state = State.CONFIRMED_INCONSISTENT_ID;
				newObservation.expirationTime = PASSIVE_ID_CHANGE_BAN_DURATION;
				return newObservation;
			}
			
			return obs;
		});
	}
	
	void passiveObservation(RPCCall suspect) {
		ObservationEntry e = new ObservationEntry();
		e.state = State.OBSERVING_PASSIVELY;
		e.lastObservedId = suspect.getResponse().getID();
		e.expirationTime = System.currentTimeMillis() + OBSERVATION_PERIOD;
		
		// updateExisting() will take care of other cases
		underObservation.putIfAbsent(suspect.getRequest().getDestination(), e);
	}
	
	void activeCheck(RPCCall suspect) {
		Key confirmedID = suspect.getResponse().getID();
		
		RPCServer srv = dht.getServerManager().getRandomServer();
		if(srv == null)
			return;
		
		// only probe in 33% of the cases
		if(ThreadLocalRandom.current().nextInt(3) > 0)
			return;
		
		InetSocketAddress addr = suspect.getRequest().getDestination();
		
		ObservationEntry currentEntry = underObservation.get(addr);

		if(currentEntry != null && System.currentTimeMillis() - currentEntry.lastActiveCheck < ACTIVE_CHECK_BACKOFF_INTERVAL) {
			return;
		}
			
		MessageBase request = null;
		
		int rnd = ThreadLocalRandom.current().nextInt(3);
		
		// some fake based on node id, some based on lookup, so probe different things
		switch(rnd % 3) {
			case 0:
				request = new PingRequest();
				break;
			case 1:
				request = new GetPeersRequest(Key.createRandomKey());
				break;
			case 2:
			default:
				request = new FindNodeRequest(Key.createRandomKey());
				break;
		}
		
		request.setDestination(addr);
			
		RPCCall probeCall = new RPCCall(request);
		probeCall.setExpectedID(confirmedID);
		probeCall.addListener(new RPCCallListener() {
			public void stateTransition(RPCCall probe2, RPCState previous, RPCState currentCallState) {
				if(currentCallState == RPCState.ERROR || currentCallState == RPCState.RESPONDED || currentCallState == RPCState.TIMEOUT) {
					long now = System.currentTimeMillis();
					
					underObservation.compute(probe2.getRequest().getDestination(), (unused, existingObservationEntry) -> {
						ObservationEntry newEntryObs = new ObservationEntry();
						
						newEntryObs.lastActiveCheck = now;
						newEntryObs.state = State.OBSERVING_PASSIVELY;
						newEntryObs.expirationTime = now + ACTIVE_CHECK_BACKOFF_INTERVAL;
						newEntryObs.lastObservedId = confirmedID;
						
						if(currentCallState == RPCState.RESPONDED) {
							newEntryObs.lastObservedId = probe2.getResponse().getID();
							if(!probe2.matchesExpectedID()) {
								newEntryObs.state = State.CONFIRMED_INCONSISTENT_ID;
								newEntryObs.expirationTime = now + ACTIVE_ID_CHANGE_BAN_DURATION;
							}
						}
						
						if(existingObservationEntry != null) {
							newEntryObs.expirationTime = Math.max(newEntryObs.expirationTime, existingObservationEntry.expirationTime);
							if(existingObservationEntry.state == State.CONFIRMED_INCONSISTENT_ID)
								newEntryObs.state = State.CONFIRMED_INCONSISTENT_ID;
						}
						
						return newEntryObs;
					});
					
					activeLookups.remove(srv, probe2);
				}
			};
		});
		
		if(activeLookups.putIfAbsent(srv, probeCall) == null)
			srv.doCall(probeCall);
		
		
	}
	
	/**
	 * @param forExpectedId if null is passed only checks for known-inconsistent nodes, otherwise it also checks whether the ID matches a recent observation
	 */
	public boolean isIdInconsistencyExpected(InetSocketAddress addr, Key forExpectedId) {
		if(merged.containsKey(addr.getAddress()))
			return true;
		ObservationEntry e = underObservation.get(addr);
		if(e == null)
			return false;
		if(e.state == State.CONFIRMED_INCONSISTENT_ID)
			return true;
		if(forExpectedId != null)
			return !e.lastObservedId.equals(forExpectedId);
		return false;
	}
	
	void purge() {
		long now = System.currentTimeMillis();
		
		underObservation.values().removeIf(e -> {
			return now > e.expirationTime;
		});
		
		merged.values().removeIf(t -> {
			return now > t;
		});
		
		underObservation.entrySet().stream().
			filter(e -> e.getValue().
			state == State.CONFIRMED_INCONSISTENT_ID).
			collect(Collectors.groupingBy(e -> e.getKey().getAddress())).forEach((k, v) -> {
				if(v.size() > 1) {
					merged.compute(k, (unused, i) -> {
						long t = v.stream().mapToLong(o -> o.getValue().expirationTime).max().getAsLong();
						return i == null ? t : Math.max(t, i);
					});
				}
			});
		
	}
	
	@Override
	public String toString() {
		return underObservation.values().stream().collect(Collectors.groupingBy(obs -> obs.state, Collectors.counting())).toString();
	}

}
