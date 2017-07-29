/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad.messages;

import lbms.plugins.mldht.kad.Key;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author Damokles
 *
 */
public abstract class AbstractLookupRequest extends MessageBase {

	protected Key	target;
	private boolean want4;
	private boolean want6;

	/**
	 * @param id
	 * @param info_hash
	 */
	public AbstractLookupRequest (Key target, Method m) {
		super(null, m, Type.REQ_MSG);
		this.target = target;
	}
	
	@Override
	public Map<String, Object> getInnerMap() {
		Map<String, Object> inner = new TreeMap<>();
		inner.put("id", id.getHash());
		inner.put(targetBencodingName(), target.getHash());
		List<String> want = new ArrayList<>(2);
		if(want4)
			want.add("n4");
		if(want6)
			want.add("n6");
		inner.put("want",want);

		return inner;

	}

	protected abstract String targetBencodingName();

	/**
	 * @return the info_hash
	 */
	public Key getTarget () {
		return target;
	}

	public boolean doesWant4() {
		return want4;
	}
	
	public void decodeWant(List<byte[]> want) {
		if(want == null)
			return;
		
		List<String> wants = new ArrayList<>(2);
		for(byte[] bytes : want)
			wants.add(new String(bytes, StandardCharsets.ISO_8859_1));
		
		want4 |= wants.contains("n4");
		want6 |= wants.contains("n6");
	}
	
	public void setWant4(boolean want4) {
		this.want4 = want4;
	}

	public boolean doesWant6() {
		return want6;
	}

	public void setWant6(boolean want6) {
		this.want6 = want6;
	}
	
	@Override
	public String toString() {
		//return super.toString() + "targetKey:"+target+" ("+(160-DHT.getSingleton().getOurID().findApproxKeyDistance(target))+")";
		return super.toString() + "targetKey:"+target;
	}
	
	/**
	 * @return the info_hash
	 */
	public Key getInfoHash () {
		return target;
	}
}
