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
package lbms.plugins.mldht.indexer.utils;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import lbms.plugins.mldht.kad.utils.BitVector;
import lbms.plugins.mldht.kad.utils.ThreadLocalUtils;

import static java.lang.Math.*;


public class GenericBloomFilter implements Cloneable {

	// bits, must be a power of 2
	private final int m; // = 1024 * 8;
	// expected entries
	private final int n; // = 2000;

	// number of hashes (bits to set per entry)
	private final int k;
	private final int hashBits;
	
	BitVector filter;


	
	
	public GenericBloomFilter(int m, int n) {
		if(Long.bitCount(m) != 1)
			throw new IllegalArgumentException("Number of bits must be a power of 2");
		this.m = m;
		this.n = n;
		k = (int) Math.max(1, Math.round(m * 1.0 / n * Math.log(2)));
		hashBits = (int) (Math.log(m)/Math.log(2));
		filter = new BitVector(m);
	}
	
	
	public void insert(ByteBuffer data) {
		MessageDigest sha1 = ThreadLocalUtils.getThreadLocalSHA1();
		sha1.reset();
		sha1.update(data);
		BitVector hash = new BitVector(160, sha1.digest());
		sha1.reset();
		for(int i=0;i<k;i++)
			filter.set(hash.rangeToInt(i*hashBits, hashBits));
	}
	
	public boolean probablyContains(ByteBuffer data)
	{
		MessageDigest sha1 = ThreadLocalUtils.getThreadLocalSHA1();
		sha1.reset();
		sha1.update(data);
		BitVector hash = new BitVector(160, sha1.digest());
		sha1.reset();
		for(int i=0;i<k;i++)
		{
			if(!filter.get(hash.rangeToInt(i*hashBits, hashBits)))
				return false;
		}
		return true;
	}
	
	public void clear() {
		filter.clear();
	}
	
	
	
	@Override
	protected GenericBloomFilter clone() {
		GenericBloomFilter newFilter = null;
		try
		{
			newFilter = (GenericBloomFilter) super.clone();
		} catch (CloneNotSupportedException e)
		{
			throw new Error("should not happen");
		}
		newFilter.filter = new BitVector(filter);
		return newFilter;
	}
	
	public double populationEstimate() {
		// number of expected 0 bits = m * (1 − 1/m)^(k*size)

		double c = filter.bitcount();
		double size = log1p(-c/m) / (k * logB());
		return size;
	}
	
	// the base for size estimates, occurs in various calculations
	private double b() {
		return 1.0 - 1.0 / m;
	}
	
	// the logarithm of the base used for various calculations
	private double logB() {
		return log1p(-1.0/m);
	}
	
	@Override
	public String toString() {
		return String.format("bits: %d/%d pop: %d/%d k: %d", filter.bitcount(), filter.size(), populationEstimate(), n, k);
	}
	
	public static void main(String[] args) throws Exception {

	}

	
	
	
}
