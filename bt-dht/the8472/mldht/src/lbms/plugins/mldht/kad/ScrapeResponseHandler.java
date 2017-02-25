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
package lbms.plugins.mldht.kad;

import java.net.InetAddress;
import java.util.*;

import lbms.plugins.mldht.kad.messages.GetPeersResponse;
import lbms.plugins.mldht.kad.utils.AddressUtils;
import lbms.plugins.mldht.utils.Blackhole;

public class ScrapeResponseHandler {
	private List<GetPeersResponse>			scrapeResponses = new ArrayList<GetPeersResponse>(20);
	private int								scrapeSeeds;
	private int								scrapePeers;
	private int								direct;
	
	
	public void addGetPeersRespone(GetPeersResponse gpr)
	{
		scrapeResponses.add(gpr);
	}
	
	public int getScrapedPeers() {
		return scrapePeers;
	}
	
	public int getScrapedSeeds() {
		return scrapeSeeds;
	}
	
	public int getDirectResultCount() {
		return direct;		
	}
	
	public void process() {
		List<BloomFilterBEP33> seedFilters = new ArrayList<BloomFilterBEP33>();
		List<BloomFilterBEP33> peerFilters = new ArrayList<BloomFilterBEP33>();
		Set<InetAddress> directPeers = new HashSet<InetAddress>();
		
		// process seeds first, we need them for some checks later (not yet implemented)
		for(int i=0;i<scrapeResponses.size();i++)
		{
			GetPeersResponse response = scrapeResponses.get(i);
			BloomFilterBEP33 f = response.getScrapeSeeds();
			if(f != null)
				seedFilters.add(f);
		}
		
		scrapeSeeds = BloomFilterBEP33.unionSize(seedFilters);
		
		for(int i=0;i<scrapeResponses.size();i++)
		{
			GetPeersResponse response = scrapeResponses.get(i);
			BloomFilterBEP33 f = response.getScrapePeers();

			Set<InetAddress> addrs = new HashSet<InetAddress>();
			
			for(DBItem item : response.getPeerItems())
			{
				if (item instanceof PeerAddressDBItem)
				{
					PeerAddressDBItem peer = (PeerAddressDBItem) item;
					if(!AddressUtils.isBogon(peer))
						addrs.add(peer.getInetAddress());
				}
			}
			
			directPeers.addAll(addrs);
			
			if(f == null)
			{
				// TODO cross-check with seed filters
				f = new BloomFilterBEP33();
				for(InetAddress addr : addrs)
					f.insert(addr);
			}
			
			peerFilters.add(f);
		}
		
		scrapePeers = BloomFilterBEP33.unionSize(peerFilters);
		
		direct = directPeers.size();
		
		// we extracted the results, no need to retain all the networking stuff, replace it with a dummy
		scrapeResponses = (List<GetPeersResponse>) Blackhole.SINGLETON;
	}
	
	
}
