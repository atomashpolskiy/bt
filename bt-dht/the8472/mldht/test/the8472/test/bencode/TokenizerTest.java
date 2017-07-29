/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.test.bencode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static the8472.bencode.Utils.str2buf;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

import org.junit.Before;
import org.junit.Test;

import the8472.bencode.Tokenizer;
import the8472.bencode.Tokenizer.TokenConsumer;
import the8472.bencode.Tokenizer.TokenType;
import the8472.bencode.Tokenizer.Token;

public class TokenizerTest {
	
	Tokenizer t;
	
	@Before
	public void readFile() throws IOException, URISyntaxException {
		this.t = new Tokenizer();
		//this.file = ByteBuffer.wrap(Files.readAllBytes(Paths.get(this.getClass().getResource(("./ubuntu-14.10-desktop-amd64.iso.torrent")).toURI())));
	}
	
	@Test
	public void correctNumberHandling() {
		ByteBuffer num = str2buf("d3:fooi-17ee");
		
		CompletableFuture<Long> parsed = new CompletableFuture<>();
		
		t.inputBuffer(num);
		t.consumer(new TokenConsumer() {
			
			@Override
			public void pop(Token st) {
				if(st.type() == TokenType.LONG)
					parsed.complete(t.lastDecodedNum());
			}
			
			@Override
			public void push(Token st) {}
		});
		t.tokenize();
		
		assertEquals(-17L, (long)parsed.getNow(0L));
	}

	@Test
	public void stopsBeforeTrailingContent() {
		ByteBuffer trailing = str2buf("de|trailing");
		
		CompletableFuture<Boolean> reachedEnd = new CompletableFuture<>();

		t.inputBuffer(trailing);
		t.consumer(new TokenConsumer() {
			
			@Override
			public void pop(Token st) {
				if(st.type() == TokenType.DICT)
					reachedEnd.complete(true);
			}
			
			@Override
			public void push(Token st) {
				// TODO Auto-generated method stub
				
			}
		});
		t.tokenize();
			
		assertEquals(2, trailing.position());
		assertTrue(reachedEnd.getNow(false));
	}

}
