/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.indexer.utils;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.concurrent.ThreadLocalRandom;

import lbms.plugins.mldht.kad.utils.BitVector;
import lbms.plugins.mldht.kad.utils.ThreadLocalUtils;

import static java.lang.Math.*;

import the8472.utils.MathUtils;


public class GenericBloomFilter implements Cloneable {

	// bits, must be a power of 2
	private final int m; // = 1024 * 8;
	// expected entries
	private final int n; // = 2000;

	// number of hashes (bits to set per entry)
	private final int k;
	private final int hashBits;
	
	BitVector filter;
	
	byte[] secret;


	static GenericBloomFilter withProbability(int n, float p) {
		if(p <= 0.0 || p >= 1.0) {
			throw new IllegalArgumentException("p must be in (0.0,1.0 range");
		}
		int m = (int) (-n*Math.log(p) / Math.pow(Math.log(2),2));
		// next power of 2
		m = Math.max(1, Integer.highestOneBit(m - 1) << 1);
		return new GenericBloomFilter(m, n);
	}
	
	public GenericBloomFilter(int m, int n) {
		if(Long.bitCount(m) != 1)
			throw new IllegalArgumentException("Number of bits must be a power of 2");
		this.m = m;
		this.n = n;
		k = (int) Math.max(1, Math.round(m * 1.0 / n * Math.log(2)));
		hashBits = (int) (Math.log(m)/Math.log(2));
		filter = new BitVector(m);
		secret = new byte[4];
		ThreadLocalRandom.current().nextBytes(secret);
	}
	
	
	public boolean insert(ByteBuffer data) {
		BitVector hash = keysForData(data);
		boolean changed = false;
		for(int i=0;i<k;i++) {
			int idx = hash.rangeToInt(i*hashBits, hashBits);
			changed |= !filter.get(idx);
			filter.set(idx);
		}
		return changed;
	}
	
	BitVector keysForData(ByteBuffer buf) {
		MessageDigest sha1 = ThreadLocalUtils.getThreadLocalSHA1();
		sha1.reset();
		int needed = hashBits * k;
		needed = (int) MathUtils.roundToNearestMultiple(MathUtils.ceilDiv(needed, 8), 20);
		byte[] buffer = new byte[needed];
		ByteBuffer hashes = ByteBuffer.wrap(buffer);
		// poor man's SHA1-CTR
		for(int i=0;i<needed/20;i++) {
			sha1.update(buf.slice());
			sha1.update(secret);
			sha1.update((byte) i);
			byte[] out = sha1.digest();
			hashes.put(out);
			sha1.reset();
		}
		return new BitVector(hashBits * k, buffer);
	}
	
	public boolean probablyContains(ByteBuffer data)
	{
		BitVector hash = keysForData(data);
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
	
	public float estimatedFalsePositiveRate() {
		//return (float) Math.pow(Math.E,(-((double)m / (double) n) * Math.pow(Math.log(2),2)));
		return (float) Math.pow(1.0 - Math.pow(Math.E, -k * (double) n / m), k);
		
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
		return String.format("bits: %d/%d pop: %.2f/%d k: %d", filter.bitcount(), filter.size(), populationEstimate(), n, k);
	}
	
	public static void main(String[] args) throws Exception {

	}

	
	
	
}
