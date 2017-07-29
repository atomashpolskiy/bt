/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad;

import static java.lang.Math.min;

import lbms.plugins.mldht.kad.tasks.KeyspaceSampler;
import lbms.plugins.mldht.kad.tasks.NodeLookup;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class TaskBuilder {
	
	Collection<DHT> dhts;
	
	public static TaskBuilder fromInstances(Collection<DHT> dhts) {
		TaskBuilder group = new TaskBuilder();
		group.dhts = dhts;
		return group;
	}
	
	@FunctionalInterface
	public interface SamplingCallback {
		void onResult(Key infohash, InetSocketAddress source, Key sourceNodeId);
	}
	
	/**
	 * This is an expensive, long-running operation causing considerable amounts of traffic, it should not be performed more frequently than once every 6 hours.
	 * 
	 * @param maxTasksPerDht use a power of 2 for optimal keyspace partitioning
	 */
	public CompletionStage<Void> sampleInfoHashes(int maxTasksPerDht, String taskName, SamplingCallback onResult) {
		CompletableFuture<Void> finished = new CompletableFuture<>();
		
		AtomicInteger counter = new AtomicInteger();
		
		dhts.stream().filter(DHT::isRunning).forEach(dht -> {
			List<RPCServer> srvs =  dht.getServerManager().getAllServers().stream().filter(RPCServer::isReachable).collect(Collectors.toList());
			
			Collections.shuffle(srvs);
			
			srvs = srvs.subList(0, min(srvs.size(), maxTasksPerDht));
			
			List<Prefix> pref = new ArrayList<>();
			pref.add(new Prefix());
			
			// partition the keyspace among tasks
			while(pref.size() < srvs.size()) {
				Prefix widest = pref.stream().min(Comparator.comparingInt(Prefix::getDepth)).get();
				pref.remove(widest);
				pref.add(widest.splitPrefixBranch(false));
				pref.add(widest.splitPrefixBranch(true));
			}
			
			srvs.forEach(srv -> {
				Prefix p = pref.remove(pref.size()-1);
				
				NodeLookup nl = new NodeLookup(p.first(), srv, dht.getNode(), false);
				nl.setInfo("seed lookup for " + taskName);
				
				counter.incrementAndGet();

				
				nl.addListener(unused -> {
					KeyspaceSampler t = new KeyspaceSampler(srv, dht.getNode(), p, nl, (c, k) -> {
						onResult.onResult(k, c.getRequest().getDestination(), c.getResponse().getID());
					});
					
					t.setInfo(taskName);

					t.addListener(unused2 -> {
						if(counter.decrementAndGet() == 0)
							finished.complete(null);
					});

					dht.getTaskManager().addTask(t);
				});
				
				dht.getTaskManager().addTask(nl);
				
			});
			
			
		});
		
		if(counter.get() == 0)
			finished.completeExceptionally(new DHTException("failed to start any tasks (no active servers?)"));
		
		return finished;
		
	}

}
