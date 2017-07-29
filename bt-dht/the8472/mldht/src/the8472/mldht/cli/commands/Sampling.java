/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.mldht.cli.commands;

import the8472.mldht.cli.CommandProcessor;
import lbms.plugins.mldht.kad.TaskBuilder;
import java.util.concurrent.CompletionStage;

public class Sampling extends CommandProcessor {
	
	@Override
	protected void process() {
		
		TaskBuilder b = TaskBuilder.fromInstances(dhts);
		CompletionStage<Void> cf = b.sampleInfoHashes(Integer.MAX_VALUE, "CLI-sampling", (k, srcaddr, srcID) -> {
			println(k.toString() + " src:" + srcaddr);
		});
		
		cf.whenComplete((v, ex) -> {
			if(ex != null) {
				printErr(ex.getMessage());
				exit(1);
				return;
			}
			exit(0);
		});
		

	}
	

}
