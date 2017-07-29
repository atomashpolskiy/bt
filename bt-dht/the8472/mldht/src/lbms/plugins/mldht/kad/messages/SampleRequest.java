/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad.messages;

import lbms.plugins.mldht.kad.DHT;
import lbms.plugins.mldht.kad.Key;

public class SampleRequest extends AbstractLookupRequest {

	public SampleRequest(Key target) {
		super(target, Method.SAMPLE_INFOHASHES);
		// TODO Auto-generated constructor stub
	}

	@Override
	protected String targetBencodingName() {
		return "target";
	}

	@Override
	public void apply(DHT dh_table) {
		dh_table.sample(this);
	}

}
