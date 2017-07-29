/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.bencode;

import java.nio.ByteBuffer;
import java.util.Arrays;

import the8472.bencode.Tokenizer.DictState;
import the8472.bencode.Tokenizer.Token;
import the8472.bencode.Tokenizer.TokenConsumer;
import the8472.bencode.Tokenizer.TokenType;

public class PathMatcher implements TokenConsumer {
	
	// want a -> v -> following key
	// push dict, push string, pop string, push map, push string, pop string, push any, pop any
	
	final ByteBuffer[] elements;
	ByteBuffer result;
	Tokenizer t;
	
	int matchDepth = 1;
	
	public PathMatcher(String... path) {
		elements = Arrays.stream(path).map(Utils::str2buf).toArray(ByteBuffer[]::new);
	}
	
	public void tokenizer(Tokenizer t) {
		this.t = t;
		t.consumer = this;
	}
	
	@Override
	public void pop(Token st) {
		int depth = t.stackIdx();
		
		//System.out.println("pop"+st+" d"+depth+" m"+matchDepth);
		
		
		if(depth == matchDepth && matchDepth - 1 == elements.length) {
			Token dict = t.atStackOffset(-1);
			if(dict.expect() == DictState.ExpectValue) {
				//System.out.println("result");
				result = t.getSlice(st);
				matchDepth = Integer.MAX_VALUE;
			}
		}
		
		if(st.type() == TokenType.STRING && matchDepth - 1 < elements.length) {
			int dictDepth = depth - 2;
			
			if(dictDepth == matchDepth) {
				Token dict = t.atStackOffset(-2);
				if(dict == null)
					return;
				if(dict.expect() == DictState.ExpectKeyOrEnd) {
					ByteBuffer val = t.getSlice(st);
					if(val.equals(elements[matchDepth - 1])) {
						//System.out.println("match");
						matchDepth++;
					}
						
				}
				
			}
		}

	}
	
	@Override
	public void push(Token st) {
		//System.out.println("push"+st+" d"+t.stackIdx());
	}
	
	public ByteBuffer match(ByteBuffer buf) {
		t.inputBuffer(buf);
		t.tokenize();
		return result;
	}

}
