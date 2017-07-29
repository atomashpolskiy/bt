/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad.messages;

import java.util.Map;

import lbms.plugins.mldht.kad.DHT;
import lbms.plugins.mldht.kad.Key;

/**
 * @author Damokles
 *
 */
public class GetPeersRequest extends AbstractLookupRequest {

	
	boolean noSeeds;
	boolean scrape;

	/**
	 * @param id
	 * @param info_hash
	 */
	public GetPeersRequest (Key info_hash) {
		super(info_hash,Method.GET_PEERS);
	}

	/* (non-Javadoc)
	 * @see lbms.plugins.mldht.kad.messages.MessageBase#apply(lbms.plugins.mldht.kad.DHT)
	 */
	@Override
	public void apply (DHT dh_table) {
		dh_table.getPeers(this);
	}
	
	@Override
	public Map<String, Object> getInnerMap() {
		Map<String, Object> innerMap = super.getInnerMap();
		
		if(noSeeds)
			innerMap.put("noseed", Long.valueOf(1));
		if(scrape)
			innerMap.put("scrape", Long.valueOf(1));
		
		return innerMap;
	}
	
	public boolean isNoSeeds() {
		return noSeeds;
	}

	public void setNoSeeds(boolean noSeeds) {
		this.noSeeds = noSeeds;
	}

	public boolean isScrape() {
		return scrape;
	}

	public void setScrape(boolean scrape) {
		this.scrape = scrape;
	}
	
	@Override
	protected String targetBencodingName() {
		return "info_hash";
	}
}
