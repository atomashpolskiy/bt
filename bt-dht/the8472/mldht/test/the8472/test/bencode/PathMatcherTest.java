/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.test.bencode;

import static org.junit.Assert.assertEquals;
import static the8472.bencode.Utils.str2buf;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;

import org.junit.Test;

import the8472.bencode.PathMatcher;
import the8472.bencode.Tokenizer;

public class PathMatcherTest {
	
	ByteBuffer match(String in) {
		PathMatcher m = new PathMatcher("foo", "bar");
		
		Tokenizer t = new Tokenizer();

		m.tokenizer(t);
		
		return m.match(str2buf(in));
	}
	
	@Test
	public void testMatcher() throws InterruptedException, ExecutionException {

		assertEquals(null, match("d3:foolee"));
		assertEquals(str2buf("le"), match("d3:food3:barleee"));
		assertEquals(str2buf("3:baz"), match("d3:food3:bar3:bazee"));
		assertEquals(str2buf("d3:bazlee"), match("d3:food3:bard3:bazleeee"));
				
	}

}
