/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Spliterator;
import java.util.concurrent.ThreadLocalRandom;

public class ShufflingBag<E> implements Collection<E>, Queue<E> {
	
	ArrayList<E> storage = new ArrayList<>();

	@Override
	public int size() {
		return storage.size();
	}

	@Override
	public boolean isEmpty() {
		return storage.isEmpty();
	}

	@Override
	public boolean contains(Object o) {
		return storage.contains(o);
	}

	@Override
	public Iterator<E> iterator() {
		return storage.iterator();
	}
	
	@Override
	public Spliterator<E> spliterator() {
		return storage.spliterator();
	}

	@Override
	public Object[] toArray() {
		return storage.toArray();
	}

	@Override
	public <T> T[] toArray(T[] a) {
		return storage.toArray(a);
	}

	@Override
	public boolean add(E e) {
		if(isEmpty()) {
			storage.add(e);
			return true;
		}
		int idx = ThreadLocalRandom.current().nextInt(storage.size());
		E old = storage.get(idx);
		storage.set(idx, e);
		return storage.add(old);
	}

	@Override
	public boolean remove(Object o) {
		return storage.remove(o);
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		return storage.containsAll(c);
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		storage.ensureCapacity(storage.size() + c.size());
		c.forEach(this::add);
		return true;
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		return storage.removeAll(c);
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		return storage.retainAll(c);
	}

	@Override
	public void clear() {
		storage.clear();
		
	}

	@Override
	public boolean offer(E e) {
		return add(e);
	}

	@Override
	public E remove() {
		if(isEmpty())
			throw new NoSuchElementException();
		return storage.remove(storage.size() - 1);
	}

	@Override
	public E poll() {
		if(!isEmpty())
			return storage.remove(storage.size() - 1);
		return null;
	}

	@Override
	public E element() {
		if(isEmpty())
			throw new NoSuchElementException();
		return storage.get(storage.size() - 1);
	}

	@Override
	public E peek() {
		return isEmpty() ? null : storage.get(storage.size() - 1);
	}
	

}
