/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad.tasks;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.stream.Collectors;

final class TaskStats implements Cloneable {
	
	private final int[] counters;
	
	public TaskStats() {
		counters = new int[CountedStat.values().length];
	}
	
	private TaskStats(int[] c) {
		counters = c;
	}
	
	void invariants() {
		assert(get(CountedStat.SENT) >= get(CountedStat.STALLED) + get(CountedStat.FAILED) + get(CountedStat.RECEIVED));
		assert(Arrays.stream(counters).noneMatch(i -> i < 0));
	}
	
	
	public TaskStats update(EnumSet<CountedStat> inc, EnumSet<CountedStat> dec, EnumSet<CountedStat> zero) {
		TaskStats p = this.clone();
		for (CountedStat counter : inc) {
			p.counters[counter.ordinal()]++;
		}
		for (CountedStat counter : dec) {
			p.counters[counter.ordinal()]--;
		}
		for (CountedStat counter : zero) {
			p.counters[counter.ordinal()]=0;
		}
		p.invariants();
		return p;
	}
	
	public int get(CountedStat c) {
		return counters[c.ordinal()];
	}
	
	@Override
	protected TaskStats clone()  {
		return new TaskStats(counters.clone());
	}
	
	public int done() {
		return get(CountedStat.FAILED) + get(CountedStat.RECEIVED);
	}
	
	public int activeOnly() {
		return unanswered() - currentStalled();
	}
	
	public int currentStalled() {
		return get(CountedStat.STALLED);
	}
	
	public int unanswered() {
		return get(CountedStat.SENT) - done();
	}
	
	@Override
	public String toString() {
		String coreVals = Arrays.stream(CountedStat.values()).map(st -> st.toString() + ":" + get(st)).collect(Collectors.joining(" "));
		return coreVals + " activeOnly:" + activeOnly() + " unanswered:" + unanswered();
	}
	
	
	
	
}
