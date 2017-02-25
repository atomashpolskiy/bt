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

import static the8472.bencode.Utils.buf2ary;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lbms.plugins.mldht.kad.BloomFilterBEP33;
import lbms.plugins.mldht.kad.DBItem;
import lbms.plugins.mldht.kad.DHT;
import lbms.plugins.mldht.kad.DHT.DHTtype;

/**
 * @author Damokles
 *
 */
public class GetPeersResponse extends AbstractLookupResponse {


	private ByteBuffer			scrapeSeeds;
	private ByteBuffer			scrapePeers;

	private List<DBItem>	items;

	/**
	 * @param mtid
	 * @param id
	 * @param nodes
	 * @param token
	 */
	public GetPeersResponse (byte[] mtid) {
		super(mtid, Method.GET_PEERS, Type.RSP_MSG);
	}
	
	
	/* (non-Javadoc)
	 * @see lbms.plugins.mldht.kad.messages.MessageBase#apply(lbms.plugins.mldht.kad.DHT)
	 */
	@Override
	public void apply (DHT dh_table) {
		dh_table.response(this);
	}
	
	@Override
	public Map<String, Object> getInnerMap() {
		Map<String, Object> innerMap = super.getInnerMap();
		if(items != null && !items.isEmpty()) {
			List<byte[]> itemsList = new ArrayList<byte[]>(items.size());
			for (DBItem item : items) {
				itemsList.add(item.getData());
			}
			innerMap.put("values", itemsList);
		}

		if(scrapePeers != null && scrapeSeeds != null)
		{
			innerMap.put("BFpe", scrapePeers);
			innerMap.put("BFse", scrapeSeeds);
		}

		return innerMap;
	}
	
	public void setPeerItems(List<DBItem> items) {
		this.items = items;
	}

	public List<DBItem> getPeerItems () {
		return items == null ? (List<DBItem>)Collections.EMPTY_LIST : Collections.unmodifiableList(items);
	}
	
	public BloomFilterBEP33 getScrapeSeeds() {
		if(scrapeSeeds != null)
			return new BloomFilterBEP33(buf2ary(scrapeSeeds));
		return null;
	}

	public void setScrapeSeeds(byte[] scrapeSeeds) {
		this.scrapeSeeds = Optional.ofNullable(scrapeSeeds).map(ByteBuffer::wrap).orElse(null);
	}
	
	public void setScrapeSeeds(BloomFilterBEP33 scrapeSeeds) {
		this.scrapeSeeds = scrapeSeeds != null ? scrapeSeeds.toBuffer() : null;
	}


	public BloomFilterBEP33 getScrapePeers() {
		if(scrapePeers != null)
			return new BloomFilterBEP33(buf2ary(scrapePeers));
		return null;
	}

	public void setScrapePeers(byte[] scrapePeers) {
		this.scrapePeers = Optional.ofNullable(scrapePeers).map(ByteBuffer::wrap).orElse(null);
	}

	public void setScrapePeers(BloomFilterBEP33 scrapePeers) {
		this.scrapePeers = scrapePeers != null ? scrapePeers.toBuffer() : null;
	}

	@Override
	public String toString() {
		return super.toString() +
			(nodes != null ? (nodes.packedSize()/DHTtype.IPV4_DHT.NODES_ENTRY_LENGTH)+" nodes | " : "") +
			(nodes6 != null ? (nodes6.packedSize()/DHTtype.IPV6_DHT.NODES_ENTRY_LENGTH)+" nodes6 | " : "") +
			(items != null ? (items.size())+" values | " : "") +
			(scrapePeers != null ? "peer bloom filter | " : "") +
			(scrapeSeeds != null ? "seed bloom filter | " :  "" );
	}
}
