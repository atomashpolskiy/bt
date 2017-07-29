/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.utils.io;

import static the8472.utils.Functional.unchecked;

import java.net.InetAddress;

public class NetMask {
	byte[] addr;
	int mask;
	
	public static NetMask fromString(String toParse) {
		String[] parts = toParse.split("/");
		return new NetMask(unchecked(() -> InetAddress.getByName(parts[0])),Integer.valueOf(parts[1]));
	}
	
	public NetMask(InetAddress addr, int mask) {
		this.mask = mask;
		this.addr = addr.getAddress();
		if(this.addr.length * 8 < mask)
			throw new IllegalArgumentException("mask cannot cover more bits than the length of the network address");
	}
	
	public boolean contains(InetAddress toTest) {
		byte[] other = toTest.getAddress();
		
		if(addr.length != other.length)
			return false;

		for(int i=0;i<mask/8;i++) {
			if(addr[i] != other[i])
				return false;
		}
		
		if(mask % 8 == 0)
			return true;
		
		int offset = mask/8;
		
		int probeMask = (0xff00 >> mask%8) & 0xff;
		
		return (addr[offset] & probeMask) == (other[offset] & probeMask);
	}
}
