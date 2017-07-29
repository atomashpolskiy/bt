/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad.messages;

import static java.lang.Math.max;
import static java.lang.Math.min;

import lbms.plugins.mldht.kad.Key;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SampleResponse extends AbstractLookupResponse {
	
	ByteBuffer samples;
	int num;
	int interval;
	
	public static int MAX_INTERVAL = 6 * 3600;

	public SampleResponse(byte[] mtid) {
		super(mtid, Method.SAMPLE_INFOHASHES, Type.RSP_MSG);
	}
	
	public void setSamples(ByteBuffer buf) {
		this.samples = buf;
	}
	
	public void setNum(int num) {
		this.num = num;
	}
	
	public int num() {
		return num;
	}
	
	public int interval() {
		return interval;
	}
	
	public void setInterval(int interval) {
		this.interval = max(0, min(interval, MAX_INTERVAL));
	}
	
	public boolean remoteSupportsSampling() {
		return samples != null;
	}
	
	public Collection<Key> getSamples() {
		if(samples == null || samples.remaining() == 0) {
			return Collections.emptyList();
		}
		
		List<Key> keys = new ArrayList<>();
		ByteBuffer copy = samples.duplicate();
		
		while(copy.hasRemaining()) {
			keys.add(new Key(copy));
		}
		
		return keys;
	}
	
	@Override
	public Map<String, Object> getInnerMap() {
		Map<String, Object> inner = super.getInnerMap();
		
		inner.put("num", num);
		inner.put("interval", interval);
		inner.put("samples", samples);
		
		return inner;
		
	}

}
