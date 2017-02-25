package lbms.plugins.mldht.kad.tasks;

import java.util.Objects;

import lbms.plugins.mldht.kad.Key;
import lbms.plugins.mldht.kad.Node;
import lbms.plugins.mldht.kad.RPCServer;

public abstract class TargetedTask extends Task {
	
	TargetedTask(Key k, RPCServer rpc, Node node) {
		super(rpc, node);
		Objects.requireNonNull(k);
		targetKey = k;
	}



	Key targetKey;
	
	
	
	public Key getTargetKey () {
		return targetKey;
	}

}
