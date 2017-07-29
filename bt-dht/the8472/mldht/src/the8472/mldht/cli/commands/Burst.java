/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.mldht.cli.commands;

import the8472.mldht.cli.CommandProcessor;

import lbms.plugins.mldht.kad.DHT;
import lbms.plugins.mldht.kad.Key;
import lbms.plugins.mldht.kad.tasks.NodeLookup;
import lbms.plugins.mldht.kad.tasks.Task;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

public class Burst extends CommandProcessor {
	
	@Override
	protected void process() {
		Thread t = new Thread(() -> {
			int count = 50;
			
			if(arguments.size() > 0)
				count = Integer.parseInt(arguments.get(0));
			
			List<DHT> dhts = new ArrayList<>(this.dhts);
			
			List<CompletableFuture<Void>> futures = new ArrayList<>();
			List<NodeLookup> tasks = new ArrayList<>(count);
			
			for(int i=0;i<count;i++) {
				CompletableFuture<Void> future = new CompletableFuture<>();
				futures.add(future);
				DHT dht = dhts.get(ThreadLocalRandom.current().nextInt(dhts.size()));
				Optional.ofNullable(dht.getServerManager().getRandomActiveServer(false)).ifPresent(rpc -> {
					NodeLookup task = new NodeLookup(Key.createRandomKey(), rpc, dht.getNode(), false);
					tasks.add(task);
					task.addListener((finishedTask) -> {
						if(!isRunning()) {
							tasks.stream().filter(remaining -> !remaining.isFinished()).forEach(Task::kill);
						}
						
						println("done: "+finishedTask+" ["+dht.getType()+"]");
						future.complete(null);
					});
					dht.getTaskManager().addTask(task);
				});
				
			}
			
			
			CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
			
			println("all done");
			
			exit(0);
		});
		
		t.setDaemon(true);
		t.setName("burst command");
		
		t.start();
	}


}
