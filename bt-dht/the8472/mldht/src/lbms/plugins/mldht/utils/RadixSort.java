/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.utils;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

import lbms.plugins.mldht.kad.Key;
import lbms.plugins.mldht.kad.Prefix;

public class RadixSort {
	
	public final static void radixSort(final Radixable[] toSort) {
		radixSort(toSort, 0, toSort.length, 0);
	}
	
	
	private final static void radixSort(final Radixable[] toSort, final int startIdx, final int endIdx, final int depth)
	{
		final int bins = 256;
			
		int[] count = new int[bins];
		
		int lowestBin = bins;
		int highestBin = 0;
		boolean ascendingRun = true;
		int prevRadix = -1;
		
		for(int i=startIdx;i<endIdx;i++)
		{
			int radix = toSort[i].getRadix(depth);
			if(lowestBin > radix)
				lowestBin = radix;
			if(highestBin < radix)
				highestBin = radix;
			if(prevRadix > radix)
				ascendingRun = false;
			prevRadix = radix;
			count[radix]++;
		}
		
		if(ascendingRun)
		{
			// shortcut
			int bucketStart = startIdx;
			for(int i=lowestBin;i<=highestBin;i++)
			{
				int bucketLength = count[i];
				int bucketEnd = bucketStart + bucketLength; 
				doRecursion(toSort, bucketStart, bucketEnd, depth);
				bucketStart = bucketEnd;
			}
			return;
		}
		
		int[] startIndices = new int[bins];
		// we'll overwrite this one on the fly
		int[] endIndices = count;
		
		startIndices[lowestBin] = startIdx;
		for(int i=lowestBin+1;i<=highestBin;i++)
		{
			
			startIndices[i] =  count[i-1] + startIndices[i-1];
			endIndices[i-1] = startIndices[i-1];
		}

		
		endIndices[highestBin] = startIndices[highestBin];
		int currentBin = lowestBin;
		
		for(int currentPointer=startIdx;currentPointer<endIdx;)
		{
			Radixable current = toSort[currentPointer];
			int radix, target = -1;
			// do a swapping dance through the array until we find something that fits into the current bin
			while((radix = current.getRadix(depth)) != currentBin)
			{
				target = endIndices[radix];
				Radixable newCurrent = toSort[target];
				toSort[target] = current;
				endIndices[radix] = target+1;
				current = newCurrent;
			}
			
			// only write current element if we did any swapping
			if(target != -1)
				toSort[currentPointer] = current;
			
			// advance the current pointer to the next element
			endIndices[ currentBin ] = ++currentPointer;  
			  

			// make sure that invariants hold
			boolean needsUpdate = false;
			
			while(currentBin < bins-1 && endIndices[ currentBin ] == startIndices[ currentBin +1 ])
			{
				int bin = currentBin++;
				needsUpdate = true;
				
				// recurse inside the loop to exploit cache locality in mostly sorted arrays
				//doRecursion(toSort,startIndices[bin],endIndices[bin],depth);
				// fudge pointers so we don't recurse into this bucket again
				//startIndices[bin] = endIndices[bin];
			}	
			
			// skip over anything we have already completed in the swapping phase
			if(needsUpdate)
				currentPointer = Math.max(currentPointer, endIndices[ currentBin ]);
		
		}
		
		for(int recursionBin = lowestBin;recursionBin<=highestBin;recursionBin++)
			doRecursion(toSort,startIndices[recursionBin ],endIndices[recursionBin ],depth);
		
	}
	
	private final static void doRecursion(final Radixable[] toSort, final int startIdx, final int endIdx, final int depth)
	{
		int inBin = endIdx - startIdx; 
		if(inBin < 2)
			return;
		if(inBin > 32)
			radixSort(toSort,startIdx,endIdx,depth+1);
		else
			Arrays.sort(toSort, startIdx, endIdx);
	}
	
	public static void main(String[] args) {
		
		Prefix p = Prefix.WHOLE_KEYSPACE;
		for(int i=0;i<64;i++)
			p = p.splitPrefixBranch(false);
		
		Key[] values = new Key[5000000];
		for(int i=0;i<values.length;i++)
			values[i] = p.createRandomKeyFromPrefix();
		
		Comparator<Key> k = new Key.DistanceOrder(Key.createRandomKey());
		
		//Arrays.sort(values);
		Collections.shuffle(Arrays.asList(values));
		
		for(int i=0;i<200;i++)
		{
			System.gc();
			Key[] toSort = values.clone();
			long start = System.nanoTime();
			radixSort(toSort);
			//Arrays.sort(toSort);
			System.out.println((System.nanoTime()-start)/1000/1000);
			for(int j=1;j<toSort.length;j++)
				if(toSort[j-1].compareTo(toSort[j]) > 1)
					System.out.println("error");
		}
	}
	
}
