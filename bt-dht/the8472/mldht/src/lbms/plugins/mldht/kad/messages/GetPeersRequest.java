/*
 *    This file is part of mlDHT.
 * 
 *    mlDHT is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation, either version 2 of the License, or
 *    (at your option) any later version.
 * 
 *    mlDHT is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 * 
 *    You should have received a copy of the GNU General Public License
 *    along with mlDHT.  If not, see <http://www.gnu.org/licenses/>.
 */
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
