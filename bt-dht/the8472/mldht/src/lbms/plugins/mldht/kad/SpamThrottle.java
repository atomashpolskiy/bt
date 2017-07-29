/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad;

import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class SpamThrottle {
	
	private Map<InetAddress, Integer> hitcounter = new ConcurrentHashMap<>();
	
	private AtomicLong lastDecayTime = new AtomicLong(System.currentTimeMillis());
	
	private static final int BURST = 10;
	private static final int PER_SECOND = 2;
	
	public boolean addAndTest(InetAddress addr) {
		int updated = saturatingAdd(addr);
		
		if(updated >= BURST)
			return true;
		
		return false;
	}
	
	public void remove(InetAddress addr) {
		hitcounter.remove(addr);
	}
	
	public boolean test(InetAddress addr) {
		return hitcounter.getOrDefault(addr, 0) >= BURST;
	}
	
	public int calculateDelayAndAdd(InetAddress addr) {
		int counter = hitcounter.compute(addr, (key, old) -> old == null ? 1 : old + 1);
		int diff = counter - BURST;
		return Math.max(diff, 0)*1000/PER_SECOND;
	}
	
	public void saturatingDec(InetAddress addr) {
		hitcounter.compute(addr, (key, old) -> old == null || old == 1 ? null : old - 1);
	}
	
	public int saturatingAdd(InetAddress addr) {
		return hitcounter.compute(addr, (key, old) -> old == null ? 1 : Math.min(old + 1, BURST));
	}
	
	public void decay() {
		long now = System.currentTimeMillis();
		long last = lastDecayTime.get();
		long deltaT = TimeUnit.MILLISECONDS.toSeconds(now - last);
		if(deltaT < 1)
			return;
		if(!lastDecayTime.compareAndSet(last, last + deltaT * 1000))
			return;
		
		int deltaC = (int) (deltaT * PER_SECOND);
		
		// minor optimization: delete first, then replace only what's left
		hitcounter.entrySet().removeIf(entry -> entry.getValue() <= deltaC);
		hitcounter.replaceAll((k, v) -> v - deltaC);
		
	}
}
