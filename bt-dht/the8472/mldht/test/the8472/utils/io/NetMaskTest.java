/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.utils.io;

import static org.junit.Assert.assertTrue;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.Test;

public class NetMaskTest {

	@Test
	public void test() throws UnknownHostException {
		NetMask everything = new NetMask(InetAddress.getByAddress(new byte[] {0,0,0,0}), 0);
		NetMask single = new NetMask(InetAddress.getByAddress(new byte[] {(byte) 0xAA,(byte) 0xAA,(byte) 0xAA,(byte) 0xAA}), 32);
		NetMask nibbleA = new NetMask(InetAddress.getByAddress(new byte[] {(byte) 0xA0,(byte) 0x00,(byte) 0x00,(byte) 0x00}), 4);
		NetMask firstByte = new NetMask(InetAddress.getByAddress(new byte[] {(byte) 0xA5,(byte) 0x00,(byte) 0x00,(byte) 0x00}), 8);
		
		assertTrue(everything.contains(InetAddress.getByAddress(new byte[] {0,0,0,0})));
		assertTrue(everything.contains(InetAddress.getByAddress(new byte[] {(byte) 0xff,(byte) 0xff,(byte) 0xff,(byte) 0xff})));
		assertTrue(single.contains(InetAddress.getByAddress(new byte[] {(byte) 0xAA,(byte) 0xAA,(byte) 0xAA,(byte) 0xAA})));
		assertTrue(nibbleA.contains(InetAddress.getByAddress(new byte[] {(byte) 0xA0,(byte) 0x00,(byte) 0x00,(byte) 0x00})));
		assertTrue(firstByte.contains(InetAddress.getByAddress(new byte[] {(byte) 0xA5,(byte) 0x00,(byte) 0x00,(byte) 0x00})));
		assertTrue(firstByte.contains(InetAddress.getByAddress(new byte[] {(byte) 0xA5,(byte) 0xff,(byte) 0xff,(byte) 0xff})));
	}

}
