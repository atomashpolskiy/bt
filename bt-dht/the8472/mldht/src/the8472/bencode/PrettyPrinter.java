/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.bencode;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class PrettyPrinter {
	
	StringBuilder builder;
	
	int nesting;
	boolean guessHuman;
	boolean truncate;
	CharSequence indent;
	
	public PrettyPrinter() {
		builder = new StringBuilder();
	}
	
	public PrettyPrinter(StringBuilder b) {
		builder = b;
	}
	
	public void indent(CharSequence i) {
		indent = i;
	}
	
	public void guessHumanReadableStringValues(boolean value) {
		guessHuman = value;
	}
	
	public void truncateHex(boolean value) {
		truncate = value;
	}
	
	enum LinebreakOmit {
		NONE, NEXT, TRY
	}
	
	LinebreakOmit previousState = LinebreakOmit.NONE;
	
	private void linebreak(LinebreakOmit state) {
		if(indent == null)
			return;
		if(state == LinebreakOmit.TRY && previousState == LinebreakOmit.NEXT) {
			previousState = LinebreakOmit.NONE;
			return;
		}
		builder.append('\n');
		for(int i=0;i<nesting;i++)
			builder.append(indent);
		previousState = state;
	}
	
	public PrettyPrinter append(Object o) {
		prettyPrintInternal(o);
		return this;
	}
	
	@Override
	public String toString() {
		return builder.toString();
	}
	
	
	static boolean containsControls(String st) {
		return st.codePoints().anyMatch(i -> i < 32 && i != '\r' && i != '\n');
	}
	
	

	void prettyPrintInternal(Object o) {
		if(o instanceof Map) {
			Map<Object,Object> m = (Map<Object, Object>) o;
			
			builder.append("{");
			nesting++;
			if(m.size() > 0)
				linebreak(LinebreakOmit.NONE);
			Iterator<Entry<Object,Object>> it = m.entrySet().iterator();
			while(it.hasNext()) {
				Map.Entry<?,?> e = it.next();
				prettyPrintInternal(e.getKey());
				builder.append(":");
				prettyPrintInternal(e.getValue());
				if(it.hasNext()) {
					builder.append(", ");
					linebreak(LinebreakOmit.NONE);
				}
			}
			nesting--;
			if(m.size() > 0)
				linebreak(LinebreakOmit.NEXT);
			builder.append("}");
			return;
		}
		
		if(o instanceof List) {
			List<?> l = (List<?>) o;
			builder.append("[");
			nesting++;
			if(l.size() > 1)
				linebreak(LinebreakOmit.NONE);
			Iterator<?> it = l.iterator();
			
			Object prev = null;
			
			while(it.hasNext()) {
				Object e = it.next();
				if(prev != null) {
					builder.append(", ");
					boolean omit = (prev instanceof List || prev instanceof Map) && (e instanceof List || e instanceof Map);
					linebreak(omit ? LinebreakOmit.TRY : LinebreakOmit.NONE);
				}
				prettyPrintInternal(e);
				prev = e;
			}
			nesting--;
			if(l.size() > 1)
				linebreak(LinebreakOmit.NEXT);
			builder.append("]");
			return;
		}
		
		if(o instanceof String) {
			String str = (String) o;
			if(containsControls(str)) {
				prettyPrintInternal(str.getBytes(StandardCharsets.ISO_8859_1));
				return;
			}
			
			builder.append('"');
			builder.append(str);
			builder.append('"');
			return;
		}
		
		if(o instanceof Long || o instanceof Integer) {
			builder.append(o);
			return;
		}
		
		if(o instanceof ByteBuffer) {
			ByteBuffer buf = ((ByteBuffer) o).slice();
			byte[] bytes;
			if(buf.hasArray() && buf.arrayOffset() == 0 && buf.capacity() == buf.limit())
				bytes = buf.array();
			else {
				bytes = new byte[buf.remaining()];
				buf.get(bytes);
			}
			o = bytes;
		}
		
		if(o instanceof byte[]) {
			byte[] bytes = (byte[]) o;
			if(bytes.length == 0) {
				builder.append("\"\"");
				return;
			}
			
			if(guessHuman) {
				CharsetDecoder dec = StandardCharsets.UTF_8.newDecoder();
				dec.onMalformedInput(CodingErrorAction.REPORT);
				dec.onUnmappableCharacter(CodingErrorAction.REPORT);
				try {
					String asString = dec.decode(ByteBuffer.wrap(bytes)).toString();
					if(!containsControls(asString)) {
						builder.append('"').append(asString).append('"');
						return;
					}
				} catch(CharacterCodingException ignored) {
					// do nothing
				}
				
			}
			
			builder.append("0x");
			if(truncate) {
				// btv2 uses 256bit hashes, so only truncate truncate > 32 bytes
				Utils.toHex(bytes, builder, 32);
				
				if(bytes.length > 32) {
					builder.append('…');
					builder.append('(');
					builder.append(bytes.length);
					builder.append(')');
				}
			} else {
				Utils.toHex(bytes, builder, Integer.MAX_VALUE);
			}
			 
			
			if(bytes.length < 10) {
				builder.append('/');
				builder.append(Utils.stripToAscii(bytes));
			}
			
			return;
		}
		
		builder.append("unhandled type(").append(o).append(')');
	}

}
