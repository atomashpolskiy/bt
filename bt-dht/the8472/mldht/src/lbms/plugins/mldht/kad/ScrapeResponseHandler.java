/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad;

import java.net.InetAddress;
import java.util.*;

import lbms.plugins.mldht.kad.messages.GetPeersResponse;
import lbms.plugins.mldht.kad.utils.AddressUtils;
import lbms.plugins.mldht.utils.Blackhole;

public class ScrapeResponseHandler {
	private List<GetPeersResponse>			scrapeResponses = new ArrayList<>(20);
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
		List<BloomFilterBEP33> seedFilters = new ArrayList<>();
		List<BloomFilterBEP33> peerFilters = new ArrayList<>();
		Set<InetAddress> directPeers = new HashSet<>();
		
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

			Set<InetAddress> addrs = new HashSet<>();
			
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
