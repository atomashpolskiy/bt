/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad.messages;

import lbms.plugins.mldht.kad.DHT;
import lbms.plugins.mldht.kad.Key;

/**
 * @author Damokles
 *
 */
public class FindNodeRequest extends AbstractLookupRequest {

	/**
	 * @param id
	 * @param target
	 */
	public FindNodeRequest (Key target) {
		super(target,Method.FIND_NODE);
	}

	/* (non-Javadoc)
	 * @see lbms.plugins.mldht.kad.messages.MessageBase#apply(lbms.plugins.mldht.kad.DHT)
	 */
	@Override
	public void apply (DHT dh_table) {
		dh_table.findNode(this);
	}

	@Override
	protected String targetBencodingName() { return "target"; }
}
