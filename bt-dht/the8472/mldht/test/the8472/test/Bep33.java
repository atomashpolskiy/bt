/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.junit.Test;

import lbms.plugins.mldht.kad.BloomFilterBEP33;
import net.i2p.crypto.eddsa.Utils;

public class Bep33 {
	
	@Test
	public void testVector() throws Exception {
		
		BloomFilterBEP33 bf = new BloomFilterBEP33();
		//2001:DB8::
		for(int i=0;i<1000;i++)
		{
			bf.insert(InetAddress.getByAddress(new byte[] {0x20,0x01,0x0D,(byte) 0xB8,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,(byte) ((i>>>8) & 0xFF),(byte) (i & 0xFF)}));
		}
		
		for(int i=0;i<256;i++)
		{
			bf.insert(InetAddress.getByAddress(new byte[] {(byte) 192,0,2,(byte) i}));
		}
		
		String[] testVector = new String[] {
		"F6C3F5EA A07FFD91 BDE89F77 7F26FB2B FF37BDB8 FB2BBAA2 FD3DDDE7 BACFFF75 EE7CCBAE",
		"FE5EEDB1 FBFAFF67 F6ABFF5E 43DDBCA3 FD9B9FFD F4FFD3E9 DFF12D1B DF59DB53 DBE9FA5B",
		"7FF3B8FD FCDE1AFB 8BEDD7BE 2F3EE71E BBBFE93B CDEEFE14 8246C2BC 5DBFF7E7 EFDCF24F",
		"D8DC7ADF FD8FFFDF DDFFF7A4 BBEEDF5C B95CE81F C7FCFF1F F4FFFFDF E5F7FDCB B7FD79B3",
		"FA1FC77B FE07FFF9 05B7B7FF C7FEFEFF E0B8370B B0CD3F5B 7F2BD93F EB4386CF DD6F7FD5",
		"BFAF2E9E BFFFFEEC D67ADBF7 C67F17EF D5D75EBA 6FFEBA7F FF47A91E B1BFBB53 E8ABFB57",
		"62ABE8FF 237279BF EFBFEEF5 FFC5FEBF DFE5ADFF ADFEE1FB 737FFFFB FD9F6AEF FEEE76B6",
		"FD8F72EF"};
		
		String hex = Arrays.stream(testVector).map(s -> s.replaceAll("\\s", "")).collect(Collectors.joining());
		
		byte[] reference = Utils.hexToBytes(hex);
		
		assertTrue(Arrays.equals(reference, bf.serialize()));
		assertEquals(1224.9308, bf.size(), 1.0);
	}

}
