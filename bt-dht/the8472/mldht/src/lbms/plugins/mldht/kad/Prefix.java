/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad;

import static the8472.utils.Arrays.mismatch;

import java.util.Collection;
import java.util.Collections;

public class Prefix extends Key {
	
	/**
	 * identifies the first bit of a key that has to be equal to be considered as covered by this prefix
	 * -1 = prefix matches whole keyspace
	 * 0 = 0th bit must match
	 * 1 = ...
	 */
	int depth = -1;
	
	public static final Prefix WHOLE_KEYSPACE = new Prefix();
	
	public Prefix() {
		super();
	}
	
	public Prefix(Prefix p) {
		super(p);
		depth = p.depth;
	}
	
	public Prefix(Key k, int depth) {
		copyBits(k, this, depth);
		this.depth = depth;
	}
	
	
	/**
	 *
	 * @param KeyTest to be checked
	 * @return true if this Prefix covers the provided key
	 */
	public boolean isPrefixOf(Key k)
	{
		return bitsEqual(this, k, depth);
	}
	
	public boolean splittable() {
		return getDepth() < Key.KEY_BITS - 1;
	}
	
	public Prefix splitPrefixBranch(boolean highBranch) {
		Prefix branch = new Prefix(this);
		int branchDepth = ++branch.depth;
		if(highBranch)
			branch.hash[branchDepth / 8] |=  0x80 >> (branchDepth % 8);
		else
			branch.hash[branchDepth / 8] &= ~(0x80 >> (branchDepth % 8));
		
				
		return branch;
	}
	
	public Key first() {
		return new Key(this);
	}
	
	public Key last() {
		Key trailingBits = new Prefix(Key.MAX_KEY, depth).distance(Key.MAX_KEY);
		return this.distance(trailingBits);
	}
	
	public Prefix getParentPrefix() {
		if(depth == -1)
			return this;
		Prefix parent = new Prefix(this);
		int oldDepth = parent.depth--;
		// set last bit to zero
		parent.hash[oldDepth / 8] &= ~(0x80 >> (oldDepth % 8));
		return parent;
	}
	
	public boolean isSiblingOf(Prefix otherPrefix)
	{
		if(depth != otherPrefix.depth)
			return false;
		
		return bitsEqual(this, otherPrefix, depth-1);
	}
	
	/**
	 * @return true if the first bits up to the Nth bit of both keys are equal
	 * 
	 * <pre>
	 *  n = -1 => no bits have to match
	 *  n = 0  => byte 0, MSB has to match
	 * </pr>
	 */
	private static boolean bitsEqual(Key k1, Key k2, int n)
	{
		if(n < 0)
			return true;
		
		byte[] h1 = k1.hash;
		byte[] h2 = k2.hash;
		
		int lastToCheck = n >>> 3;
		
		int mmi = mismatch(h1, h2);
		
		int diff = (h1[lastToCheck] ^ h2[lastToCheck]) & 0xff;
		
		boolean lastByteDiff = (diff & (0xff80 >>> (n & 0x07))) == 0;
		
		return mmi == lastToCheck ? lastByteDiff : Integer.compareUnsigned(mmi, lastToCheck) > 0;
	}
	
	private static void copyBits(Key source, Key destination, int depth)
	{
		if(depth < 0)
			return;
		
		byte[] data = destination.hash;
		
		// copy over all complete bytes
		for (int i = 0; i < depth / 8; i++)
			data[i] = source.hash[i];
		
		int idx = depth / 8;
		int mask = 0xFF80 >> depth % 8;
		
		// mask out the part we have to copy over from the last prefix byte
		data[idx] &= ~mask;
		// copy the bits from the last byte
		data[idx] |= source.hash[idx] & mask;
	}
	
	public int getDepth() {
		return depth;
	}
	
	@Override
	public String toString() {
		if(depth == -1)
			return "all";
		StringBuilder builder = new StringBuilder(depth+3);
		for(int i=0;i<=depth;i++)
			builder.append((hash[i/8] & (0x80 >> (i % 8))) != 0 ? '1' : '0');
		builder.append("...");
		return builder.toString();
			
			
	}
	
	/**
	 * Generates a random Key that has falls under this prefix
	 */
	public Key createRandomKeyFromPrefix() {
		// first generate a random one
		Key key = Key.createRandomKey();
		
		copyBits(this, key, depth);

		return key;
	}
	
	/*
	public Key lowestKey() {
		Key k = new Key();
		copyBits(this, k, depth);
		
		return k;
	}
	
	public Key highestKey() {
		Key k = new Key(Key.MAX_KEY);
		copyBits(this, k, depth);
		
		return k;
	}*/
	
	public static Prefix getCommonPrefix(Collection<Key> keys)
	{
		if(keys.isEmpty())
			throw new IllegalArgumentException("keys cannot be empty");
		
		Key first = Collections.min(keys);
		Key last = Collections.max(keys);

		Prefix prefix = new Prefix();
		byte[] newHash = prefix.hash;

		outer: for(int i=0;i<SHA1_HASH_LENGTH;i++)
		{
			if(first.hash[i] == last.hash[i])
			{
				newHash[i] = first.hash[i];
				prefix.depth += 8;
				continue;
			}
			// first differing byte
			newHash[i] = (byte)(first.hash[i] & last.hash[i]);
			for(int j=0;j<8;j++)
			{
				int mask = 0x80 >> j;
				// find leftmost differing bit and then zero out all following bits
				if(((first.hash[i] ^ last.hash[i]) & mask) != 0)
				{
					newHash[i] = (byte)(newHash[i] & ~(0xFF >> j));
					break outer;
				}
				
				prefix.depth++;
			}
		}
		return prefix;
	}
	
	public static void main(String[] args) {
		Prefix p = new Prefix();
		p.hash[0] = (byte) 0x30;
		p.depth = 3;
		
		Key k = new Key();
		k.hash[0] = (byte) 0x37;
		
		System.out.println(p);
		System.out.println(p.isPrefixOf(k));
		
	
	}
	
}
