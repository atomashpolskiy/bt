/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad.utils;

import java.nio.ByteBuffer;
import java.util.Arrays;


public final class BitVector {
	
	private final byte[] vector;
	private final int bits;
	

	public BitVector(BitVector toCopy) {
		bits = toCopy.bits;
		vector = toCopy.vector.clone();
	}
	
	public BitVector(int numBits, byte[] rawData)
	{
		if(numBits > rawData.length * 8)
			throw new IllegalArgumentException("raw data array too small to represent the requested number of bits");
		bits = numBits;
		vector = rawData.clone();
	}
	
	
	public BitVector(int numberOfBits)
	{
		bits = numberOfBits;
		vector = new byte[numberOfBits/8 + (numberOfBits % 8 != 0 ? 1 : 0)];
	}
	
	public void set(int n) {
		vector[n/8] |= 0x01 << n % 8;
	}
	
	public boolean get(int n) {
		return (vector[n/8] & 0x01 << n % 8) != 0;
	}
	
	/**
	 * reads an arbitrary (even non-aligned) range of bits (up to 32) and interprets them as int (bigendian)
	 */
	public int rangeToInt(int bitOffset, int numOfBits) {
		int result = 0;
		int baseShift = numOfBits - 8 + bitOffset % 8;
		int byteIdx = bitOffset/8;
		while(baseShift >= 0)
		{
			result |= vector[byteIdx] << baseShift;
			byteIdx++;
			baseShift -= 8;
		}
		
		if(baseShift < 0)
			result |= vector[byteIdx] >>> Math.abs(baseShift);
		
		result &= 0xFFFFFFFF >>> 32 - numOfBits;
		
		return result;
	}
	
	public int size() {
		return bits;
	}
	
	public void clear() {
		Arrays.fill(vector, (byte)0);
	}
	
	public int bitcount() {
		int c = 0;
		for(int i = 0;i<bits;i++)
		{
			if((vector[i/8] & (0x01 << i % 8)) != 0)
				c++;
		}
		
		return c;
	}
	
	public static int unionAndCount(BitVector... vectors) {
		if(vectors.length == 0)
			return 0;
			
		int c = 0;
		int bits = vectors[0].size();
		byte union = 0;
		for(int i = 0;i<bits;i++)
		{
			if(i % 8 == 0)
			{
				int idx = i/8;
				union = (byte) 0x00;
				for(BitVector v : vectors)
					union |= v.vector[idx];
			}
			if((union & (0x01 << i % 8)) != 0)
				c++;
		}
		
		return c;
	}
	
	@Override
	public String toString() {
		StringBuilder b = new StringBuilder(2 * bits/8);
		for (int i = 0; i < vector.length; i++) {
			if (i % 4 == 0 && i > 0) {
				b.append(' ');
			}
			int nibble = (vector[i] & 0xF0) >> 4;
			b.append((char)(nibble < 0x0A ? '0'+nibble : 'A'+nibble-10 ));
			nibble = vector[i] & 0x0F;
			b.append((char)(nibble < 0x0A ? '0'+nibble : 'A'+nibble-10 ));
		}
		return b.toString();
	}
	
	
	
	public static int intersectAndCount(BitVector... vectors) {
		int c = 0;
		int bits = vectors[0].size();
		byte intersection = 0;
		for(int i = 0;i<bits;i++)
		{
			if(i % 8 == 0)
			{
				int idx = i/8;
				intersection = (byte) 0xFF;
				for(BitVector v : vectors)
					intersection &= v.vector[idx];
			}
			if((intersection & (0x01 << i % 8)) != 0)
				c++;
		}
		
		return c;
	}
	
	public byte[] getSerializedFormat() {
		return vector.clone();
	}
	
	public ByteBuffer toBuffer() {
		return ByteBuffer.wrap(vector).asReadOnlyBuffer();
	}
	
	public static void main(String[] args) {
		BitVector bv = new BitVector(40, new byte[] {(byte) 0xF0,(byte) 0xFF,(byte) 0xFF,(byte) 0xFF,(byte) 0xFF,(byte) 0xFF,(byte) 0xFF,(byte) 0xFF,(byte) 0xFF});
		System.out.println(bv.rangeToInt(7, 2));
	}
	
}
