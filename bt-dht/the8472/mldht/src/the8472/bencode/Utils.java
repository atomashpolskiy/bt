/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.bencode;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class Utils {
	
	/*
	 * all byte[]/buffer/String conversions use ISO_8859_1 by default because it's round-trip compatible to unicode codepoints 0-255. i.e. it's suitable for binary data of unspecified encodings.
	 * 
	 * this will garble actual UTF-8 strings, decode those manually if it's meant to be human-readable
	 */
	
	public static void str2buf(String in, ByteBuffer out) {
		if(out.remaining() < in.length())
			throw new BufferOverflowException();
		for(int i=0;i<in.length();i++) {
			char c = in.charAt(i);
			if(c > 0xff)
				throw new IllegalArgumentException("only strings with codepoints 0x00 - 0xff are supported. for proper unicode handling convert strings manually. attempted to encode: "+in);
			out.put((byte) c);
		}
	}
	
	public static ByteBuffer str2buf(String str) {
		return ByteBuffer.wrap(str.getBytes(StandardCharsets.ISO_8859_1));
	}
	
	public static String buf2str(ByteBuffer buf) {
		byte[] arr;
		int offset;
		int len;
		
		if(buf.hasArray()) {
			arr = buf.array();
			offset = buf.arrayOffset() + buf.position();
			len = buf.remaining();
		} else {
			arr = new byte[buf.remaining()];
			buf.get(arr, buf.position(), arr.length);
			offset = 0;
			len = arr.length;
		}
		
		return new String(arr,offset,len, StandardCharsets.ISO_8859_1);
	}
	
	public static byte[] buf2ary(ByteBuffer buf) {
		byte[] out = new byte[buf.remaining()];
		buf.get(out);
		return out;
	}
	
	public static byte[] str2ary(String str) {
		return str.getBytes(StandardCharsets.ISO_8859_1);
	}
	
	public static String prettyPrint(Object o) {
		StringBuilder b = new StringBuilder(1024);
		new PrettyPrinter(b).prettyPrintInternal(o);
		return b.toString();
	}
	
	public static String stripToAscii(byte[] arr) {
		return stripToAscii(ByteBuffer.wrap(arr));
	}
	
	public static String stripToAscii(ByteBuffer buf) {
		
		int length = buf.remaining();
				
		char[] out = new char[buf.remaining()];
		for(int i=0;i<length ;i++) {
			char b = (char)(buf.get(buf.position() + i) & 0xff);
			if(b < ' ' || b > '~')
				b = '�';
			out[i] = b;
		}
		return new String(out);
	}
	
	static void toHex(byte[] toHex, StringBuilder builder, int maxBytes)
	{
		if(toHex.length < maxBytes)
			maxBytes = toHex.length;
		builder.ensureCapacity(maxBytes * 2);
		for (int i = 0; i < maxBytes; i++) {
			int nibble = (toHex[i] & 0xF0) >> 4;
			builder.append((char)(nibble < 0x0A ? '0'+nibble : 'A'+nibble-10 ));
			nibble = toHex[i] & 0x0F;
			builder.append((char)(nibble < 0x0A ? '0'+nibble : 'A'+nibble-10 ));
		}
	}
	
	
	public static byte[] hex2ary(CharSequence hex) {
		byte[] bytes = new byte[hex.length()/2];
		
	    for (int i = 0; i < hex.length(); i += 2)
	        bytes[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i+1), 16));
	    
	    return bytes;
	}
	


}
