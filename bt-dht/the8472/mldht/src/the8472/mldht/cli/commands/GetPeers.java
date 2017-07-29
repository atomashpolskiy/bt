/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.mldht.cli.commands;

import the8472.mldht.cli.CommandProcessor;
import the8472.mldht.cli.ParseArgs;

import lbms.plugins.mldht.kad.DHT;
import lbms.plugins.mldht.kad.Key;
import lbms.plugins.mldht.kad.tasks.PeerLookupTask;
import lbms.plugins.mldht.utils.NIOConnectionManager;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class GetPeers extends CommandProcessor {
	
	NIOConnectionManager conMan;
	
	ScheduledThreadPoolExecutor timer;

	@Override
	protected void process() {
		
		boolean fast = ParseArgs.extractBool(arguments, "-fast");
		boolean nocache = ParseArgs.extractBool(arguments, "-nocache");
		
		List<Key> hashes = arguments.stream()
				.filter(Key.STRING_PATTERN.asPredicate())
				.map(st -> new Key(st))
				.collect(Collectors.toCollection(ArrayList::new));
		
		if(hashes.isEmpty())
			hashes.add(Key.createRandomKey());
		
		AtomicInteger counter = new AtomicInteger();
		Instant start = Instant.now();
		
		hashes.forEach(h -> {
			dhts.stream().filter(DHT::isRunning).map(DHT::getServerManager).map(m -> m.getRandomActiveServer(false)).filter(Objects::nonNull).forEach(d -> {
				DHT dht = d.getDHT();
				
				PeerLookupTask t = new PeerLookupTask(d, dht.getNode(), h);
				
				t.setNoAnnounce(true);
				t.setFastTerminate(fast);
				t.useCache(!nocache);
				
				counter.incrementAndGet();
				
				t.addListener(unused -> {
					if(counter.decrementAndGet() == 0)
						exit(0);
				});
				
				//t.useCache(false);
				
				t.setResultHandler((source, item) -> {
					Formatter f = new Formatter();
					
					Duration elapsed = Duration.between(start, Instant.now());
					
					f.format("%-5dms %s %s from: %s", elapsed.toMillis(), h.toString(), item.toString(), source);
					
					println(f.toString());
				});
				
				dht.getTaskManager().addTask(t);
			});
		});
		
		
		
	}
	

}
