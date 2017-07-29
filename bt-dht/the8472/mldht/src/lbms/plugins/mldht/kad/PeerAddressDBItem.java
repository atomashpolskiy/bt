/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import lbms.plugins.mldht.kad.DHT.DHTtype;
import the8472.bencode.Utils;

public class PeerAddressDBItem extends DBItem {
	
	
	boolean seed;
	byte[] originatorVersion;
	
	public static PeerAddressDBItem createFromAddress(InetAddress addr, int port, boolean isSeed) {
		byte[] tdata = new byte[addr.getAddress().length + 2];
		ByteBuffer bb = ByteBuffer.wrap(tdata);
		bb.put(addr.getAddress());
		bb.putShort((short) port);
		return new PeerAddressDBItem(tdata, isSeed);
	}
	
	public PeerAddressDBItem(byte[] data, boolean isSeed) {
		super(data);
		if(data.length != DHTtype.IPV4_DHT.ADDRESS_ENTRY_LENGTH && data.length != DHTtype.IPV6_DHT.ADDRESS_ENTRY_LENGTH)
			throw new IllegalArgumentException("byte array length does not match ipv4 or ipv6 raw InetAddress+Port length");
		seed = isSeed;
	}
	
	public void setVersion(byte[] ary) {
		originatorVersion = ary;
	}
	
	public InetAddress getInetAddress() {
		try
		{
			if (item.length == DHTtype.IPV4_DHT.ADDRESS_ENTRY_LENGTH)
				return InetAddress.getByAddress(Arrays.copyOf(item, 4));
			if (item.length == DHTtype.IPV6_DHT.ADDRESS_ENTRY_LENGTH)
				return InetAddress.getByAddress(Arrays.copyOf(item, 16));
		} catch (UnknownHostException e)
		{
			// should not happen
			e.printStackTrace();
		}
		
		return null;
	}
	
	public InetSocketAddress toSocketAddress() {
		return new InetSocketAddress(getAddressAsString(), getPort());
	}
	
	public String getAddressAsString() {
		return getInetAddress().getHostAddress();
	}
	
	public Class<? extends InetAddress> getAddressType() {
		if(item.length == DHTtype.IPV4_DHT.ADDRESS_ENTRY_LENGTH)
			return DHTtype.IPV4_DHT.PREFERRED_ADDRESS_TYPE;
		if(item.length == DHTtype.IPV6_DHT.ADDRESS_ENTRY_LENGTH)
			return DHTtype.IPV6_DHT.PREFERRED_ADDRESS_TYPE;
		return null;
	}

	@Override
	public boolean equals(Object obj) {
		if(obj instanceof PeerAddressDBItem)
		{
			PeerAddressDBItem other = (PeerAddressDBItem) obj;
			if(other.item.length != item.length)
				return false;
			for(int i=0;i<item.length-2;i++)
				if(other.item[i] != item[i])
					return false;
			return true;
		}
		return false;
	}
	
	@Override
	public int hashCode() {
		return Arrays.hashCode(Arrays.copyOf(item, item.length-2));
	}

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder(25);
		b.append(" addr:");
		b.append(toSocketAddress());
		b.append(" seed:");
		b.append(seed);
		if(originatorVersion != null)
			b.append(" version:").append(Utils.prettyPrint(originatorVersion));

		return b.toString();
	}
	
	public int getPort() {
		if (item.length == DHTtype.IPV4_DHT.ADDRESS_ENTRY_LENGTH)
			return (item[4] & 0xFF) << 8 | (item[5] & 0xFF);
		if (item.length == DHTtype.IPV6_DHT.ADDRESS_ENTRY_LENGTH)
			return (item[16] & 0xFF) << 8 | (item[17] & 0xFF);
		return 0;
	}
	
	public boolean isSeed() {
		return seed;
	}
	
}
