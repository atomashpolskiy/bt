/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.test.bencode;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static the8472.bencode.Utils.str2ary;
import static the8472.bencode.Utils.str2buf;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import the8472.bencode.BDecoder;

public class DecoderTest {
	
	BDecoder decoder;
	
	@Before
	public void setup() {
		decoder = new BDecoder();
	}

	@Test
	public void test() {
		ByteBuffer buf = str2buf("d3:fool3:bari1337ee3:zzz3:baze");
		Map<String, Object> rootMap = decoder.decode(buf);
		assertNotNull(rootMap);
		assertEquals(2, rootMap.size());
		List<Object> list = (List<Object>) rootMap.get("foo");
		byte[] secondValue = (byte[]) rootMap.get("zzz");
		assertArrayEquals(str2ary("baz"), secondValue);
		assertNotNull(list);
		assertEquals(2, list.size());
		assertArrayEquals(str2ary("bar"),(byte[])list.get(0));
		assertEquals(1337, (long)list.get(1));
	}

}
