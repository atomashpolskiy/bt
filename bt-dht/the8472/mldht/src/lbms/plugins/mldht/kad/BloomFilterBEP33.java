/*
 *    This file is part of mlDHT.
 * 
 *    mlDHT is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation, either version 2 of the License, or
 *    (at your option) any later version.
 * 
 *    mlDHT is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 * 
 *    You should have received a copy of the GNU General Public License
 *    along with mlDHT.  If not, see <http://www.gnu.org/licenses/>.
 */
package lbms.plugins.mldht.kad;

import static java.lang.Math.log1p;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;

import lbms.plugins.mldht.kad.utils.BitVector;


public class BloomFilterBEP33 implements Comparable<BloomFilterBEP33>, Cloneable {

	public final static int m = 256 * 8;
	private final static int k = 2;

	
	MessageDigest sha1;
	BitVector filter;
	
	public BloomFilterBEP33() {
		filter = new BitVector(m);
		
		try
		{
			sha1 = MessageDigest.getInstance("SHA1");
		} catch (NoSuchAlgorithmException e)
		{
			e.printStackTrace();
		}
	}
	
	public BloomFilterBEP33(byte[] serializedFilter) {
		filter = new BitVector(m,serializedFilter);
	}
	
    public void insert(InetAddress addr) {
        
        byte[] hash = sha1.digest(addr.getAddress());
        
        int index1 = (hash[0]&0xFF) | (hash[1]&0xFF) << 8;
        int index2 = (hash[2]&0xFF) | (hash[3]&0xFF) << 8;

        // truncate index to m (11 bits required)
        index1 %= m;
        index2 %= m;

        // set bits at index1 and index2
        filter.set(index1);
        filter.set(index2);
    }
	
	
	@Override
	protected BloomFilterBEP33 clone() {
		BloomFilterBEP33 newFilter = null;
		try
		{
			newFilter = (BloomFilterBEP33) super.clone();
		} catch (CloneNotSupportedException e)
		{
			throw new Error("never happens");
		}
		newFilter.filter = new BitVector(filter);
		return newFilter;
	}
	
	public int compareTo(BloomFilterBEP33 o) {
		return size()-o.size();
	}

	
	public int size() {
		// number of expected 0 bits = m * (1 − 1/m)^(k*size)

		double c = filter.bitcount();
		double size = log1p(-c/m) / (k * logB());
		return (int) size;
	}
	
	public static int unionSize(Collection<BloomFilterBEP33> filters)
	{
		BitVector[] vectors = new BitVector[filters.size()];
		int i = 0;
		for(BloomFilterBEP33 f : filters)
			vectors[i++] = f.filter;
		
		double c = BitVector.unionAndCount(vectors);
		return (int) (log1p(-c/m) / (k * logB()));
	}
	
	public byte[] serialize() {
		return filter.getSerializedFormat();
	}
	
	public ByteBuffer toBuffer() {
		return filter.toBuffer();
	}

	
	// the logarithm of the base used for various calculations
	private static double logB() {
		return log1p(-1.0/m);
	}

}
