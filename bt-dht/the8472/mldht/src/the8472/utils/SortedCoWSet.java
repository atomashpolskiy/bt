/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.utils;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class SortedCoWSet<T> {
	
	final Comparator<? super T> comparator;
	final Class<T> clazz;
	final Object copyOnWriteLock = new Object();
		
	volatile T[] storage;
	
	public SortedCoWSet(Class<T> clazz, Comparator<T> comp) {
		this.comparator = comp;
		this.clazz = clazz;
		storage = (T[]) Array.newInstance(clazz, 0);
	}
	
	/**
	 * @return the current backing array. consumers must not modify it
	 */
	public T[] getSnapshot() {
		return storage;
	}
	
	public boolean contains(T toCheck) {
		return java.util.Arrays.binarySearch(storage, toCheck, comparator) >= 0;
	}
	
	public int size() {
		return storage.length;
	}
	
	public boolean add(T toAdd) {
		synchronized (copyOnWriteLock) {
			int insertIndex = java.util.Arrays.binarySearch(storage, toAdd, comparator);
			if(insertIndex >= 0) {
				// already present
				return false;
			}
			
			insertIndex = -(insertIndex + 1);
			
			T[] newStorage = java.util.Arrays.copyOf(storage, storage.length+1);
			if(newStorage.length > 1)
				System.arraycopy(newStorage, insertIndex, newStorage, insertIndex +1, newStorage.length - insertIndex - 1);
			newStorage[insertIndex] = toAdd;
			storage = newStorage;
		}
		return true;
	}
	
	public void removeIf(Predicate<T> matcher) {
		synchronized (copyOnWriteLock) {
			ArrayList<T> newSet = new ArrayList<>();

			Arrays.asList(storage).stream().filter(e -> !matcher.test(e)).collect(Collectors.toCollection(() -> newSet));
			if(newSet.size() != storage.length) {
				Collections.sort(newSet, comparator);
				storage = newSet.toArray((T[]) Array.newInstance(clazz, newSet.size()));
			}
				
		}
	}
		

}
