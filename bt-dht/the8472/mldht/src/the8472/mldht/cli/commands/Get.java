/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.mldht.cli.commands;

import static the8472.bencode.Utils.hex2ary;

import the8472.mldht.cli.CommandProcessor;

import lbms.plugins.mldht.kad.DHT;
import lbms.plugins.mldht.kad.Key;
import lbms.plugins.mldht.kad.tasks.GetLookupTask;

import java.util.Formatter;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class Get extends CommandProcessor {

	@Override
	protected void process() {
		Key k;
		byte[] salt = null;
		try {
			k = new Key(arguments.get(0));
			if(arguments.size() > 1) {
				salt = hex2ary(arguments.get(1));
			}
		} catch (Exception e) {
			handleException(e);
			return;
		}
		
		AtomicInteger completion = new AtomicInteger();
		
		final byte[] fSalt = salt;
		
		dhts.stream().filter(DHT::isRunning).map(DHT::getServerManager).map(m -> m.getRandomActiveServer(false)).filter(Objects::nonNull).forEach(s -> {
			GetLookupTask g = new GetLookupTask(k, s, s.getDHT().getNode());
			
			g.expectedSalt(fSalt);
			
			g.setValueConsumer((storage) -> {
				Formatter f = new Formatter();
				storage.debugString(f);
				println(f.toString());
			});
			
			g.addListener(l -> {
				if(completion.decrementAndGet() == 0) {
					done();
				}
				
			});
			
			s.getDHT().getTaskManager().addTask(g);
			
			completion.incrementAndGet();
		});
		
		if(completion.get() == 0) {
			println("no tasks started");
			done();
		}
		
	}
	
	void done() {
		exit(0);
	}

}
