/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.test.bencode;

import static org.junit.Assert.assertEquals;
import static the8472.bencode.Utils.buf2str;
import static the8472.bencode.Utils.str2buf;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import the8472.bencode.BEncoder;

public class EncoderTest {

	@Test
	public void testBasics() {
		Map<String, Object> root = new LinkedHashMap<>();
		List<Object> l = new ArrayList<>();
		l.add(1234L);
		l.add(str2buf("foo"));
		
		root.put("zz", 3L);
		root.put("xx", 1L);
		root.put("yy", 1L);
		root.put("aa", 1L);
		root.put("bb", 1L);
		root.put("c", l);
		
		BEncoder enc = new BEncoder();
		ByteBuffer out = enc.encode(root, 1024);
		
		assertEquals(str2buf("d2:aai1e2:bbi1e1:cli1234e3:fooe2:xxi1e2:yyi1e2:zzi3ee"), out);
	}
	
	@Test
	public void testSorting() {
		Map<String, Object> root = new LinkedHashMap<>();
		
		String[] keys = new String[256];
		
		Arrays.setAll(keys, (i) -> buf2str(ByteBuffer.wrap(new byte[] {(byte) i})));
		// clone to shuffle independently of original order
		List<String> keyList = Arrays.asList(keys);
		List<String> shuffledKeys = Arrays.asList(keys.clone());
		Collections.shuffle(shuffledKeys);
		
		shuffledKeys.forEach(s -> root.put(s, 1L));
		
		BEncoder enc = new BEncoder();
		ByteBuffer out = enc.encode(root, 2048);
		
		StringBuilder reference = new StringBuilder();
		
		reference.append("d");
		
		keyList.forEach(str -> {
			reference.append("1:");
			reference.append(str);
			reference.append("i1e");
		});
		
		
		reference.append("e");
		
		
		
		assertEquals(str2buf(reference.toString()), out);
	}

}
