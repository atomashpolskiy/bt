/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad.utils;

import static the8472.utils.Functional.unchecked;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.ProtocolFamily;
import java.net.SocketException;
import java.net.StandardProtocolFamily;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

import lbms.plugins.mldht.kad.PeerAddressDBItem;
import the8472.utils.Arrays;

public class AddressUtils {
	
	public static boolean isBogon(PeerAddressDBItem item)
	{
		return isBogon(item.getInetAddress(), item.getPort());
	}
	
	public static boolean isBogon(InetSocketAddress addr)
	{
		return isBogon(addr.getAddress(),addr.getPort());
	}
	
	public static boolean isBogon(InetAddress addr, int port)
	{
		return !(port > 0 && port <= 0xFFFF && isGlobalUnicast(addr));
	}
	
	public static boolean isTeredo(InetAddress addr) {
		if(!(addr instanceof Inet6Address))
			return false;
		byte[] raw = addr.getAddress();
		return raw[0] == 0x20 && raw[1] == 0x01 && raw[2] == 0x00 && raw[3] == 0x00;
	}
	
	public static boolean isGlobalUnicast(InetAddress addr)
	{
		if(addr instanceof Inet4Address && addr.getAddress()[0] == 0)
			return false;
		return !(addr.isAnyLocalAddress() || addr.isLinkLocalAddress() || addr.isLoopbackAddress() || addr.isMulticastAddress() || addr.isSiteLocalAddress());
	}
	
	public static byte[] packAddress(InetSocketAddress addr) {
		byte[] result = null;
		int port = addr.getPort();
		
		if(addr.getAddress() instanceof Inet4Address) {
			result = new byte[6];
		}
		
		if(addr.getAddress() instanceof Inet6Address) {
			result = new byte[18];
		}
		
		ByteBuffer buf = ByteBuffer.wrap(result);
		buf.put(addr.getAddress().getAddress());
		buf.putChar((char)(addr.getPort() & 0xffff));
		
		return result;
	}
	
	public static List<InetSocketAddress> unpackCompact(byte[] raw, Class<? extends InetAddress> type) {
		if(raw == null || raw.length == 0)
			return Collections.emptyList();
		int addressSize = 0;
		if(type == Inet4Address.class)
			addressSize = 6;
		if(type == Inet6Address.class)
			addressSize = 18;
		if((raw.length % addressSize) != 0)
			throw new IllegalArgumentException("ipv4 / ipv6 compact format length must be multiple of 6 / 18 bytes");
		InetSocketAddress[] addrs = new InetSocketAddress[raw.length / addressSize];
		
		ByteBuffer buf = ByteBuffer.wrap(raw);
		byte[] ip = new byte[addressSize - 2];
		
		int i = 0;
		while(buf.hasRemaining()) {
			buf.get(ip);
			addrs[i] = new InetSocketAddress(unchecked(() -> InetAddress.getByAddress(ip)), Short.toUnsignedInt(buf.getShort()));
			i++;
		}
		
		return java.util.Arrays.asList(addrs);
	}
	
	public static InetSocketAddress unpackAddress(byte[] raw) {
		if(raw.length != 6 && raw.length != 18)
			return null;
		ByteBuffer buf = ByteBuffer.wrap(raw);
		byte[] rawIP = new byte[raw.length - 2];
		buf.get(rawIP);
		int port = buf.getChar();
		InetAddress ip;
		try {
			ip = InetAddress.getByAddress(rawIP);
		} catch (UnknownHostException e) {
			return null;
		}
		return new InetSocketAddress(ip, port);
	}
	
	
	static Stream<InetAddress> allAddresses() {
		try {
			return Collections.list(NetworkInterface.getNetworkInterfaces()).stream().filter(iface -> {
				try {
					return iface.isUp();
				} catch (SocketException e) {
					e.printStackTrace();
					return false;
				}
			}).flatMap(iface -> Collections.list(iface.getInetAddresses()).stream());
		} catch (SocketException e) {
			e.printStackTrace();
			return Stream.empty();
		}
	}
	
	
	public static Stream<InetAddress> nonlocalAddresses() {
		return allAddresses().filter(addr -> {
			return !addr.isAnyLocalAddress() && !addr.isLoopbackAddress();
		});
	}

	public static List<InetAddress> getAvailableGloballyRoutableAddrs(Class<? extends InetAddress> type) {
		
		LinkedList<InetAddress> addrs = new LinkedList<>();

		try {
			for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
				if (!iface.isUp() || iface.isLoopback())
					continue;
				for (InterfaceAddress ifaceAddr : iface.getInterfaceAddresses()) {
					if (type == Inet6Address.class && ifaceAddr.getAddress() instanceof Inet6Address) {
						Inet6Address addr = (Inet6Address) ifaceAddr.getAddress();
						// only accept globally reachable IPv6 unicast addresses
						if (addr.isIPv4CompatibleAddress() || !isGlobalUnicast(addr))
							continue;

						// prefer other addresses over teredo
						if (isTeredo(addr))
							addrs.addLast(addr);
						else
							addrs.addFirst(addr);
					}

					if (type == Inet4Address.class && ifaceAddr.getAddress() instanceof Inet4Address) {
						Inet4Address addr = (Inet4Address) ifaceAddr.getAddress();

						if (!isGlobalUnicast(addr))
							continue;

						addrs.add(addr);
					}
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		
		Collections.sort(addrs, (a, b) -> Arrays.compareUnsigned(a.getAddress(), b.getAddress()));
		
		
		return addrs;
	}
	
	public static boolean isValidBindAddress(InetAddress addr) {
		// we don't like them them but have to allow them
		if(addr.isAnyLocalAddress())
			return true;
		try {
			NetworkInterface iface = NetworkInterface.getByInetAddress(addr);
			if(iface == null)
				return false;
			return iface.isUp() && !iface.isLoopback();
		} catch (SocketException e) {
			return false;
		}
	}
	
	public static InetAddress getAnyLocalAddress(Class<? extends InetAddress> type) {
		try {
			if(type == Inet6Address.class)
				return InetAddress.getByAddress(new byte[16]);
			if(type == Inet4Address.class)
				return InetAddress.getByAddress(new byte[4]);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		
		throw new RuntimeException("this shouldn't happen");
	}
	
	public static InetAddress getDefaultRoute(Class<? extends InetAddress> type) {
		InetAddress target = null;
		
		ProtocolFamily family = type == Inet6Address.class ? StandardProtocolFamily.INET6 : StandardProtocolFamily.INET;
		
		try(DatagramChannel chan=DatagramChannel.open(family)) {
			if(type == Inet4Address.class)
				target = InetAddress.getByAddress(new byte[] {8,8,8,8});
			if(type == Inet6Address.class)
				target = InetAddress.getByName("2001:4860:4860::8888");
			
			chan.connect(new InetSocketAddress(target,63));
			
			InetSocketAddress soa = (InetSocketAddress) chan.getLocalAddress();
			InetAddress local = soa.getAddress();
			
			if(type.isInstance(local) && !local.isAnyLocalAddress())
				return local;
			return null;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	public static String toString(InetSocketAddress sockAddr) {
		InetAddress addr = sockAddr.getAddress();
		if(addr instanceof Inet6Address)
			return String.format("%41s:%-5d", "[" + addr.getHostAddress() + "]", sockAddr.getPort());
		return String.format("%15s:%-5d", addr.getHostAddress(), sockAddr.getPort());
	}
	
}
