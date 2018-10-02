package lbms.plugins.mldht.kad.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.hamcrest.core.IsInstanceOf;
import org.junit.Test;

public class AddressUtilsTest {
	
	@Test
	public void testGlobalUnicastMatcher() throws UnknownHostException {
		assertTrue(AddressUtils.isGlobalUnicast(InetAddress.getByName("8.8.8.8")));
		assertTrue(AddressUtils.isGlobalUnicast(InetAddress.getByName("2001:4860:4860::8888")));
		// wildcard
		assertFalse(AddressUtils.isGlobalUnicast(InetAddress.getByName("0.0.0.0")));
		assertFalse(AddressUtils.isGlobalUnicast(InetAddress.getByName("0.150.0.0")));
		assertFalse(AddressUtils.isGlobalUnicast(InetAddress.getByName("::0")));
		// loopback
		assertFalse(AddressUtils.isGlobalUnicast(InetAddress.getByName("127.0.0.15")));
		assertFalse(AddressUtils.isGlobalUnicast(InetAddress.getByName("::1")));
		// private/LL
		assertFalse(AddressUtils.isGlobalUnicast(InetAddress.getByName("192.168.13.47")));
		assertFalse(AddressUtils.isGlobalUnicast(InetAddress.getByName("169.254.1.0")));
		assertFalse(AddressUtils.isGlobalUnicast(InetAddress.getByName("fe80::")));
		// ULA
		assertFalse(AddressUtils.isGlobalUnicast(InetAddress.getByName("fc00::")));
		assertFalse(AddressUtils.isGlobalUnicast(InetAddress.getByName("fd00::")));
	}
	
	@Test
	public void testMappedBypass() throws UnknownHostException {
		byte[] v4mapped = new byte[16];
		v4mapped[11] = (byte) 0xff;
		v4mapped[10] = (byte) 0xff;
				
		assertThat(InetAddress.getByAddress(v4mapped), IsInstanceOf.instanceOf(Inet4Address.class));
		assertThat(AddressUtils.fromBytesVerbatim(v4mapped), IsInstanceOf.instanceOf(Inet6Address.class));
		
	}

}
