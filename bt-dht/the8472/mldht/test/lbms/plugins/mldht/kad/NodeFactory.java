/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad;

import static the8472.utils.Functional.unchecked;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ThreadLocalRandom;

import lbms.plugins.mldht.DHTConfiguration;
import lbms.plugins.mldht.kad.DHT.DHTtype;

public class NodeFactory {

	public static InetAddress generateIp(byte subnet) {
		// generate valid unicast IPs from 2001:20xx::/32
		byte[] addr = new byte[16];
		ThreadLocalRandom.current().nextBytes(addr);
		addr[0] = 0x20;
		addr[1] = 0x01;
		addr[2] = 0x20;
		addr[3] = subnet;
	
		return unchecked(() -> InetAddress.getByAddress(addr));
	}
	
	static DHT buildDHT(DHT.DHTtype type) {
		DHT dht = new DHT(type);
		dht.config = new DHTConfiguration() {
			
			@Override
			public boolean noRouterBootstrap() {
				// TODO Auto-generated method stub
				return false;
			}
			
			@Override
			public boolean isPersistingID() {
				// TODO Auto-generated method stub
				return false;
			}
			
			@Override
			public Path getStoragePath() {
				// TODO Auto-generated method stub
				return Paths.get(".", "does", "not", "exist");
			}
			
			@Override
			public int getListeningPort() {
				// TODO Auto-generated method stub
				return 0;
			}
			
			@Override
			public boolean allowMultiHoming() {
				// TODO Auto-generated method stub
				return false;
			}
		};
		dht.populate();
		
		return dht;
		
	}
	
	static DHT buildDHT() {
		return buildDHT(DHTtype.IPV6_DHT);
	}

	static void fillTable(Node node) {
		for(int i=0;i<1000;i++) {
			KBucketEntry e = new KBucketEntry(new InetSocketAddress(generateIp((byte)0x00), 1024), Key.createRandomKey());
			e.signalResponse(DHTConstants.RPC_CALL_TIMEOUT_MAX);
			node.insertEntry(e, true);
		}
		node.rebuildAddressCache();
	}

}
