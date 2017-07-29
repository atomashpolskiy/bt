/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad.messages;

import java.nio.ByteBuffer;
import java.util.Map;

import the8472.bencode.BEncoder;

public class GetResponse extends AbstractLookupResponse {

	public GetResponse(byte[] mtid) {
		super(mtid, Method.GET, Type.RSP_MSG);
	}
	
	
	@Override
	public Map<String,Object> getInnerMap() {
		Map<String, Object> map = super.getInnerMap();
		
		if(signature != null)
			map.put("sig", signature);
		if(key != null)
			map.put("k", key);
		if(sequenceNumber > -1)
			map.put("seq", sequenceNumber);
		
		if(rawValue != null)
			map.put("v", new BEncoder.RawData(rawValue));
		
		return map;
		
	};
	
	
	ByteBuffer rawValue;
	byte[] signature;
	long sequenceNumber = -1;
	byte[] key;
	
	public ByteBuffer getRawValue() {
		return rawValue;
	}
	public void setRawValue(ByteBuffer rawValue) {
		this.rawValue = rawValue;
	}
	public byte[] getSignature() {
		return signature;
	}
	public void setSignature(byte[] signature) {
		this.signature = signature;
	}
	public long getSequenceNumber() {
		return sequenceNumber;
	}
	public void setSequenceNumber(long sequenceNumber) {
		this.sequenceNumber = sequenceNumber;
	}
	public byte[] getPubkey() {
		return key;
	}
	public void setKey(byte[] key) {
		this.key = key;
	}
	
	@Override
	public String toString() {
		return super.toString() +
			(rawValue != null ? "rawval: " + rawValue.remaining() + "bytes " : "") +
			(signature != null ? "sig: " + signature.length + "bytes " : "") +
			(sequenceNumber != -1 ? "seq: " + sequenceNumber + " " : "") +
			(key != null ? "key: " + key.length + "bytes " : "")
		;
	}

}
