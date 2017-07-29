/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad.messages;

import lbms.plugins.mldht.kad.DHT;

/**
 * @author Damokles
 *
 */
public class UnknownTypeResponse extends AbstractLookupResponse {
	public UnknownTypeResponse (byte[] mtid) {
		super(mtid, Method.UNKNOWN, Type.RSP_MSG);
	}

	@Override
	public void apply (DHT dh_table) {
		throw new UnsupportedOperationException("incoming, unknown responses cannot be applied, they may only exist to send error messages");
	}
}
