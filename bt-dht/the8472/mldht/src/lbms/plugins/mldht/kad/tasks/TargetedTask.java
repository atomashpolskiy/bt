/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
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
