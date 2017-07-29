/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import lbms.plugins.mldht.kad.KBucketEntry;
import lbms.plugins.mldht.kad.Key;
import lbms.plugins.mldht.kad.NodeFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import org.junit.Test;

public class Bep42 {
	
	@Test
	public void testBep42Parsing() throws UnknownHostException {
		if(KBucketEntry.crc32c == null)
			return;
		
		/*
		124.31.75.21   1   5fbfbf f10c5d6a4ec8a88e4c6ab4c28b95eee4 01
		21.75.31.124  86   5a3ce9 c14e7a08645677bbd1cfe7d8f956d532 56
		65.23.51.170  22   a5d432 20bc8f112a3d426c84764f8c2a1150e6 16
		84.124.73.14  65   1b0321 dd1bb1fe518101ceef99462b947a01ff 41
		43.213.53.83  90   e56f6c bf5b7c4be0237986d5243b87aa6d5130 5a
		*/
		
		assertTrue(new KBucketEntry(new InetSocketAddress(InetAddress.getByName("124.31.75.21"), 1), new Key("5fbfbff10c5d6a4ec8a88e4c6ab4c28b95eee401")).hasSecureID());
		assertTrue(new KBucketEntry(new InetSocketAddress(InetAddress.getByName("21.75.31.124"), 1), new Key("5a3ce9c14e7a08645677bbd1cfe7d8f956d53256")).hasSecureID());
		assertTrue(new KBucketEntry(new InetSocketAddress(InetAddress.getByName("65.23.51.170"), 1), new Key("a5d43220bc8f112a3d426c84764f8c2a1150e616")).hasSecureID());
		assertTrue(new KBucketEntry(new InetSocketAddress(InetAddress.getByName("84.124.73.14"), 1), new Key("1b0321dd1bb1fe518101ceef99462b947a01ff41")).hasSecureID());
		assertTrue(new KBucketEntry(new InetSocketAddress(InetAddress.getByName("43.213.53.83"), 1), new Key("e56f6cbf5b7c4be0237986d5243b87aa6d51305a")).hasSecureID());
		
		assertFalse(new KBucketEntry(new InetSocketAddress(NodeFactory.generateIp((byte) 1), 1), Key.createRandomKey()).hasSecureID());
		
	}

}
