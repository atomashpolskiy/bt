/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad;

import static the8472.bencode.Utils.buf2ary;

import the8472.bencode.BDecoder;
import the8472.bencode.BEncoder;
import the8472.bencode.Utils;

import lbms.plugins.mldht.kad.messages.GetResponse;
import lbms.plugins.mldht.kad.messages.PutRequest;
import lbms.plugins.mldht.kad.utils.ThreadLocalUtils;

import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Collections;
import java.util.Formatter;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import net.i2p.crypto.eddsa.EdDSAEngine;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAParameterSpec;
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec;

public class GenericStorage {
	
	public static final long EXPIRATION_INTERVAL_SECONDS = 2*60*60;
	
	public static StorageItem buildMutable(Object data, EdDSAPrivateKey key, byte[] salt, long sequenceNumber) throws InvalidKeyException, SignatureException {
		ByteBuffer raw = new BEncoder().encode(data, 1000);
		
		Signature sig = new EdDSAEngine();
		sig.initSign(key);

		Map<String, Object> p = new TreeMap<>();
		
		if(salt != null)
			p.put("salt", salt);
		p.put("seq", sequenceNumber);
		p.put("v", new BEncoder.RawData(raw));
		
		ByteBuffer buf = new BEncoder().encode(p, 1500);
		
		// trim d ... e
		buf.position(buf.position() + 1);
		buf.limit(buf.limit() - 1);
		
		sig.update(buf.duplicate());
		
		byte[] signature = sig.sign();
		
		byte[] pubkey = new EdDSAPublicKey(new EdDSAPublicKeySpec(key.getA(), StorageItem.spec)).getA().toByteArray();
	
		
		
		return new StorageItem(buf2ary(raw), pubkey, signature, salt, sequenceNumber);
	}
	
	/**
	 * @param data any bencodable object that encodes to 1000 <= bytes
	 */
	public static StorageItem buildImmutable(Object data) {
		ByteBuffer raw = new BEncoder().encode(data, 1000);
		
		return new StorageItem(buf2ary(raw));
	}
	
	public static class StorageItem {
		
		StorageItem(byte[] value, byte[] pubkey, byte[] signature, byte[] salt, long sequenceNumber) {
			Objects.requireNonNull(value);
			Objects.requireNonNull(pubkey);
			Objects.requireNonNull(signature);
			this.value = value;
			this.pubkey = pubkey;
			this.signature = signature;
			this.salt = salt;
			this.sequenceNumber = sequenceNumber;
		}
		
		StorageItem(byte[] value) {
			Objects.requireNonNull(value);
			this.value = value;
			salt = null;
			pubkey = null;
		}
		
		public StorageItem(PutRequest req) {
			expirationDate = System.currentTimeMillis() + EXPIRATION_INTERVAL_SECONDS*1000;
			value = buf2ary(req.getValue());
			
			if(req.getPubkey() != null) {
				sequenceNumber = req.getSequenceNumber();
				signature = req.getSignature();
				salt = req.getSalt();
				pubkey = req.getPubkey();
			} else {
				pubkey = null;
				salt = null;
			}
		}
		
		public StorageItem(GetResponse rsp, byte[] expectedSalt) {
			value = buf2ary(rsp.getRawValue());

			if(rsp.getPubkey() != null) {
				sequenceNumber = rsp.getSequenceNumber();
				signature = rsp.getSignature();
				this.salt = expectedSalt;
				pubkey = rsp.getPubkey();
			} else {
				pubkey = null;
				this.salt = null;
			}
			
			
		}
		
		long expirationDate;
		long sequenceNumber = -1;
		byte[] signature;
		final byte[] pubkey;
		final byte[] salt;
		byte[] value;
		
		
		public boolean mutable() {
			return pubkey != null;
		}
		
		public static final EdDSAParameterSpec spec = EdDSANamedCurveTable.getByName("Ed25519");
		
		public boolean validateSig()  {
			try {
				Signature sig = new EdDSAEngine();
				sig.initVerify(new EdDSAPublicKey(new EdDSAPublicKeySpec(pubkey, spec)));
				
				// ("4:salt" length-of-salt ":" salt) "3:seqi" seq "e1:v" len ":" and the encoded value
				
				Map<String, Object> p = new TreeMap<>();
				
				if(salt != null)
					p.put("salt", salt);
				p.put("seq", sequenceNumber);
				p.put("v", new BEncoder.RawData(ByteBuffer.wrap(value)));
				
				ByteBuffer buf = new BEncoder().encode(p, 1500);
				
				// trim d ... e
				buf.position(buf.position() + 1);
				buf.limit(buf.limit() - 1);
				
				sig.update(buf);
				
				return sig.verify(signature);
			} catch (InvalidKeyException | SignatureException e) {
				return false;
			}

		}
		
		public long seq() {
			return sequenceNumber;
		}
		
		public ByteBuffer getRawValue() {
			return ByteBuffer.wrap(value).asReadOnlyBuffer();
		}
		
		public Object getDecodedValue() {
			return new BDecoder().decodeAny(ByteBuffer.wrap(value));
		}
		
		
		public Key fingerprint() {
			return GenericStorage.fingerprint(pubkey, salt, ByteBuffer.wrap(value));
		}
		
		public void debugString(Formatter f) {
			
			
			f.format("%s mutable:%b seq:%d %n",
					fingerprint(),
					mutable(),
					seq()
				);
			f.format("%s%n%n", Utils.stripToAscii(getRawValue()));
		}
		
		public long getSequenceNumber() {
			return sequenceNumber;
		}
		
		public Optional<ByteBuffer> pubKey() {
			return Optional.ofNullable(pubkey).map(ByteBuffer::wrap).map(ByteBuffer::asReadOnlyBuffer);
		}
		
		public Optional<ByteBuffer> salt() {
			return Optional.ofNullable(salt).map(ByteBuffer::wrap).map(ByteBuffer::asReadOnlyBuffer);
		}
		
		public Optional<ByteBuffer> sig() {
			return Optional.ofNullable(signature).map(ByteBuffer::wrap).map(ByteBuffer::asReadOnlyBuffer);
		}
		
		
	}
	
	ConcurrentHashMap<Key, StorageItem> items = new ConcurrentHashMap<>();
	
	
	enum UpdateResult {
		SUCCESS,
		IMMUTABLE_SUBSTITUTION_FAIL,
		SIG_FAIL,
		CAS_FAIL,
		SEQ_FAIL;
	}
	
	public static Key fingerprint(byte[] pubkey, byte[] salt, ByteBuffer buf) {
		MessageDigest dig = ThreadLocalUtils.getThreadLocalSHA1();
		
		dig.reset();
		
		if(pubkey != null) {
			dig.update(pubkey);
			if(salt != null)
				dig.update(salt);
			return new Key(dig.digest());
		}
		dig.update(buf.duplicate());
		return new Key(dig.digest());
		
	}
	
	
	public UpdateResult putOrUpdate(Key k, StorageItem newItem, long expected) {
		
		if(newItem.mutable() && !newItem.validateSig())
			return UpdateResult.SIG_FAIL;
		
		while(true) {
			StorageItem oldItem = items.putIfAbsent(k, newItem);
			
			if(oldItem == null)
				return UpdateResult.SUCCESS;
			
			if(oldItem.mutable()) {
				if(!newItem.mutable())
					return UpdateResult.IMMUTABLE_SUBSTITUTION_FAIL;
				if(newItem.sequenceNumber < oldItem.sequenceNumber)
					return UpdateResult.SEQ_FAIL;
				if(expected >= 0 && oldItem.sequenceNumber >= 0 && oldItem.sequenceNumber != expected)
					return UpdateResult.CAS_FAIL;
			}
			
			if(items.replace(k, oldItem, newItem))
				break;
		}
		
		return UpdateResult.SUCCESS;
	}
	
	public Optional<StorageItem> get(Key k) {
		return Optional.ofNullable(items.get(k));
	}
	
	
	public void cleanup() {
		long now = System.currentTimeMillis();
		
		items.entrySet().removeIf(entry -> {
			return entry.getValue().expirationDate < now;
		});
	}
	
	public Map<Key, StorageItem> getItems() {
		return Collections.unmodifiableMap(items);
	}

}
