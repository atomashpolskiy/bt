/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static the8472.bencode.Utils.hex2ary;
import static the8472.bencode.Utils.str2ary;

import the8472.bencode.BDecoder;

import lbms.plugins.mldht.kad.DHT.DHTtype;
import lbms.plugins.mldht.kad.GenericStorage;
import lbms.plugins.mldht.kad.GenericStorage.StorageItem;
import lbms.plugins.mldht.kad.Key;
import lbms.plugins.mldht.kad.messages.MessageDecoder;
import lbms.plugins.mldht.kad.messages.MessageException;
import lbms.plugins.mldht.kad.messages.PutRequest;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.SignatureException;
import java.util.Map;
import java.util.TreeMap;

import org.junit.Test;

import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec;

public class Bep44 {
	
	static byte[] privkeyraw = hex2ary("e06d3183d14159228433ed599221b80bd0a5ce8352e4bdf0262f76786ef1c74db7e7a9fea2c0eb269d61e3b38e450a22e754941ac78479d6c54e1faf6037881d");
	
	EdDSAPrivateKey buildPk() {
		return new EdDSAPrivateKey(new EdDSAPrivateKeySpec( GenericStorage.StorageItem.spec, privkeyraw));
	}
	
	@Test
	public void testMutable() throws InvalidKeyException, SignatureException, IOException, MessageException {
		byte[] value =  str2ary("12:Hello World!");
		byte[] pubkey =  new BigInteger("77ff84905a91936367c01360803104f92432fcd904a43511876df5cdf3e7e548", 16).toByteArray();
		byte[] sig = new BigInteger("305ac8aeb6c9c151fa120f120ea2cfb923564e11552d06a5d856091e5e853cff1260d3f39e4999684aa92eb73ffd136e6f4f3ecbfda0ce53a1608ecd7ae21f01", 16).toByteArray();
		Key target = new Key("4a533d47ec9c7d95b1ad75f576cffc641853b750");
		
		StorageItem it = GenericStorage.buildMutable("Hello World!", buildPk(), null, 1);
		
		StorageItem item = roundTripPut(it);
		
		assertTrue(item.validateSig());
		assertEquals(item.fingerprint(), target);
	}
	
	StorageItem roundTripPut(StorageItem in) throws IOException, MessageException {
		// create message from item
		PutRequest req = new PutRequest();
		req.populateFromStorage(in);
		req.setID(Key.createRandomKey());
		req.setMTID(new byte[4]);
		req.setToken(new byte[4]);

		// encode
		ByteBuffer encoded = ByteBuffer.allocate(1500);
		req.encode(encoded);
		
		// decode
		Map<String, Object> decoded = new BDecoder().decode(encoded.duplicate());
		MessageDecoder decoder = new MessageDecoder(null, DHTtype.IPV4_DHT);
		decoder.toDecode(encoded, decoded);
		PutRequest roundTripped = (PutRequest) decoder.parseMessage();
		
		// re-create item from round-tripped message
		return new StorageItem(roundTripped);
	}

	@Test
	public void testSaltedMutable() throws InvalidKeyException, SignatureException, IOException, MessageException {
		byte[] expectedRawValue =  str2ary("12:Hello World!");
		byte[] salt = str2ary("foobar");
		byte[] expectedPubkey =  hex2ary("77ff84905a91936367c01360803104f92432fcd904a43511876df5cdf3e7e548");
		byte[] expectedSignature = hex2ary("6834284b6b24c3204eb2fea824d82f88883a3d95e8b4a21b8c0ded553d17d17ddf9a8a7104b1258f30bed3787e6cb896fca78c58f8e03b5f18f14951a87d9a08");
		Key expectedTarget = new Key("411eba73b6f087ca51a3795d9c8c938d365e32c1");
		
		StorageItem it = GenericStorage.buildMutable("Hello World!", buildPk(), salt, 1);
		
		assertTrue(it.validateSig());
		
		StorageItem item = roundTripPut(it);

		assertTrue(item.validateSig());
		assertEquals(item.fingerprint(), expectedTarget);
		assertEquals(item.getRawValue(), ByteBuffer.wrap(expectedRawValue));
		assertEquals(item.pubKey().get(), ByteBuffer.wrap(expectedPubkey));
		assertEquals(item.sig().get(), ByteBuffer.wrap(expectedSignature));
	}
	
	@Test
	public void testImmutable() {
		byte[] expectedValue =  str2ary("12:Hello World!");
		Key expectedKey = new Key("e5f96f6f38320f0f33959cb4d3d656452117aadb");
		
		StorageItem item = GenericStorage.buildImmutable("Hello World!");
		
		assertEquals(item.getRawValue(), ByteBuffer.wrap(expectedValue));
		assertEquals(item.fingerprint(), expectedKey);
		
		
		PutRequest req = new PutRequest();
		
		req.populateFromStorage(item);
		
		assertEquals(req.deriveTargetKey(), expectedKey);
	}
	
	@Test
	public void testStructuredValue() throws InvalidKeyException, SignatureException, IOException, MessageException {
		Map<String, Object> data = new TreeMap<>();
		data.put("v", new byte[20]);
		
		StorageItem in = GenericStorage.buildMutable(data, buildPk(), new byte[]{0,13,127,0}, 1);
		StorageItem out = roundTripPut(in);
		assertTrue(out.validateSig());
		assertTrue(out.getDecodedValue() instanceof Map);
		Map<String, Object> outVal = (Map<String, Object>) out.getDecodedValue();
		assertTrue(outVal.containsKey("v"));
	}
	
	
}
