/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad.utils;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

import org.junit.Test;

import lbms.plugins.mldht.kad.utils.ResponseTimeoutFilter.Snapshot;

public class TimeoutFilterStatsTest {

	@Test
	public void testStatisticalProperties() {
		ResponseTimeoutFilter f = new ResponseTimeoutFilter();
		
		// decay otherwise sensible defaults
		IntStream.range(0, 10).forEach(i -> f.decay());
		
		f.update(300);
		f.update(7000);

		IntStream.range(0, 300).forEach(i -> f.update(1500));
		IntStream.range(0, 300).forEach(i -> f.update(1600));
		IntStream.range(0, 300).forEach(i -> f.update(1700));
		IntStream.range(0, 300).forEach(i -> f.update(1800));
		IntStream.range(0, 300).forEach(i -> f.update(1900));
		IntStream.range(0, 1000).forEach(i -> f.update(3000));
		f.newSnapshot();
		Snapshot s = f.snapshot;
		
		int expectedAvg = (1500*300 + 1600*300 + 1700*300 + 1800 * 300 + 1900 * 300 + 1000 * 3000) / (300 * 5 + 1000);
		
		assertEquals(expectedAvg, s.mean, ResponseTimeoutFilter.BIN_SIZE);
		assertEquals(3000, s.mode, ResponseTimeoutFilter.BIN_SIZE);
		assertEquals(300, s.getQuantile(0.0001f), ResponseTimeoutFilter.BIN_SIZE);
		assertEquals(1500, s.getQuantile(0.01f), ResponseTimeoutFilter.BIN_SIZE);
		assertEquals(1900, s.getQuantile(0.5f), ResponseTimeoutFilter.BIN_SIZE);
		assertEquals(3000, s.getQuantile(0.9f), ResponseTimeoutFilter.BIN_SIZE);
		assertEquals(7000, s.getQuantile(0.9999f), ResponseTimeoutFilter.BIN_SIZE);
	}
	
	@Test
	public void testCorrectnessUnderDecay() {
		ResponseTimeoutFilter f = new ResponseTimeoutFilter();
		
		IntStream.range(0, 2000).forEach(i -> {
			f.update((long) (ThreadLocalRandom.current().nextGaussian() * 100 + 5000));
			if((i % 10) == 0)
				f.decay();
		});
		
		
		
		f.newSnapshot();
		Snapshot s = f.snapshot;
		
		System.out.println(s);
		
		assertEquals(5000, s.mean, ResponseTimeoutFilter.BIN_SIZE);
		assertEquals(5000, s.getQuantile(0.5f), ResponseTimeoutFilter.BIN_SIZE);
		assertEquals(5000, s.mode, 2 * ResponseTimeoutFilter.BIN_SIZE);
		

				
	}

}
