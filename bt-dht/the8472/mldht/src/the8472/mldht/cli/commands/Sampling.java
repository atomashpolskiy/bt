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
