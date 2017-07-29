/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.bencode;

import static the8472.bencode.Utils.buf2ary;
import static the8472.bencode.Utils.buf2str;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import the8472.bencode.Tokenizer.BDecodingException;
import the8472.bencode.Tokenizer.Token;
import the8472.bencode.Tokenizer.TokenConsumer;

public class BDecoder {
	
	private class Consumer implements TokenConsumer {
		
		Object[] stack = new Object[256];
		
		String keyPendingInsert;
		
		int depth = 0;
		
		@Override
		public void push(Token st) {
			
			//System.out.println("push"+st.type());
			
			switch(st.type()) {
				case DICT:
					Object o = new HashMap<String, Object>();
					putObject(o);
					pushInternal(o);
					break;
				case LIST:
					o = new ArrayList<>();
					putObject(o);
					pushInternal(o);
					break;
				case LONG:
				case STRING:
				case PREFIXED_STRING:
					return;
				default:
					throw new IllegalStateException("this shouldn't be happening");
			}
			
			
			
			depth++;
		}
		
		void pushInternal(Object o) {
			stack[depth] = o;
		}
		
		void putObject(Object o) {
			if(depth == 0) {
				stack[0] = o;
				return;
			}
			
			Object container = stack[depth - 1];
			
			
			if(container.getClass() == HashMap.class) {
				if(keyPendingInsert != null) {
					if(o instanceof ByteBuffer)
						o = buf2ary((ByteBuffer)o);
					if(((HashMap<String, Object>)container).put(keyPendingInsert, o) != null)
						throw new BDecodingException("duplicate key found in dictionary");
					keyPendingInsert = null;
					
				} else {
					keyPendingInsert = buf2str((ByteBuffer)o);
				}
			} else if(container.getClass() == ArrayList.class) {
				if(o instanceof ByteBuffer)
					o = buf2ary((ByteBuffer)o);
				((ArrayList<Object>)container).add(o);
			} else {
				throw new RuntimeException("this should not happen");
			}

		}

		@Override
		public void pop(Token st) {
			//System.out.println("pop"+st.type());
			
			switch(st.type()) {
				case DICT:
				case LIST:
					depth--;
					return;
				case LONG:
					putObject(t.lastDecodedNum);
					break;
				case STRING:
					putObject(t.getSlice(st));
					break;
				case PREFIXED_STRING:
					return;
				default:
					throw new IllegalStateException("this shouldn't be happening");
			}
			
		}
		
		void reset() {
			Arrays.fill(stack, null);
			keyPendingInsert = null;
			depth = 0;
		}
		

	}
	
	
	public Map<String, Object> decode(ByteBuffer buf) {
		Object root = decodeInternal(buf);
		if(root instanceof Map) {
			return (Map<String, Object>) root;
		}
		throw new BDecodingException("expected dictionary as root object");
	}
	
	public Object decodeAny(ByteBuffer buf) {
		return decodeInternal(buf);
	}
	
	final Tokenizer t;
	final Consumer c;
	
	public BDecoder() {
		t = new Tokenizer();
		c = new Consumer();
		t.consumer(c);
	}
	
	private Object decodeInternal(ByteBuffer buf) {
		try {
			t.inputBuffer(buf);
			t.tokenize();
			
			return c.stack[0];
		} finally {
			// promptly release references and clean up any possibly illegal states to allow safe reuse
			t.reset();
			c.reset();
		}
	}


}
