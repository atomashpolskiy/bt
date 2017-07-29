/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.indexer.utils;

import java.nio.ByteBuffer;

public class RotatingBloomFilter {
	
	GenericBloomFilter current;
	GenericBloomFilter previous;
	int insertCount;
	int targetSize;
	boolean autorotate;
	
	public RotatingBloomFilter(int targetSize, int bitCount) {
		this.targetSize = targetSize;
		current = new GenericBloomFilter(bitCount, targetSize);
		previous = new GenericBloomFilter(bitCount, targetSize);
	}
	
	public void setAutoRotate(boolean val) {
		autorotate = val;
	}
	
	
	public void insert(ByteBuffer data)
	{
		current.insert(data);
		insertCount++;
		if(autorotate && insertCount >= targetSize)
			rotate();
	}
	
	public boolean contains(ByteBuffer data)
	{
		return current.probablyContains(data) || previous.probablyContains(data);
	}
	
	
	public void rotate() {
		GenericBloomFilter toSwap = current;
		current = previous;
		current.clear();
		previous = toSwap;
		insertCount = 0;
		
	}
	
	
	
	
}
