/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.utils;

import java.util.*;

/**
 * similar to the empty Collections instances but allows writes too and simply discards them.
 */ 
public class Blackhole<E> implements List<E>, Queue<E>, Set<E>, RandomAccess {
	
	public static final Blackhole<?> SINGLETON = new Blackhole<>();

	@Override
	public boolean offer(E e) {
		return true;
	}

	@Override
	public E remove() {
		throw new NoSuchElementException();
	}

	@Override
	public E poll() {
		return null;
	}

	@Override
	public E element() {
		return null;
	}

	@Override
	public E peek() {
		return null;
	}

	@Override
	public int size() {
		return 0;
	}

	@Override
	public boolean isEmpty() {
		return true;
	}

	@Override
	public boolean contains(Object o) {
		return false;
	}

	@Override
	public Iterator<E> iterator() {
		return Collections.EMPTY_LIST.iterator();
	}

	@Override
	public Object[] toArray() {
		return new Object[0];
	}

	@Override
	public <T> T[] toArray(T[] a) {
		return a;
	}

	@Override
	public boolean add(E e) {
		return true;
	}

	@Override
	public boolean remove(Object o) {
		return false;
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		return false;
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		return true;
	}

	@Override
	public boolean addAll(int index, Collection<? extends E> c) {
		return true;
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		return false;
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		return false;
	}

	@Override
	public void clear() {}

	@Override
	public E get(int index) {
		throw new IndexOutOfBoundsException("Index: "+index);
	}

	@Override
	public E set(int index, E element) {
		return null;
	}

	@Override
	public void add(int index, E element) {
		
	}

	@Override
	public E remove(int index) {
		return null;
	}

	@Override
	public int indexOf(Object o) {
		return -1;
	}

	@Override
	public int lastIndexOf(Object o) {
		return -1;
	}

	@Override
	public ListIterator<E> listIterator() {
		return Collections.EMPTY_LIST.listIterator();
	}

	@Override
	public ListIterator<E> listIterator(int index) {
		return Collections.EMPTY_LIST.listIterator();
	}

	@Override
	public List<E> subList(int fromIndex, int toIndex) {
		return this;
	}
	
	@Override
	public Spliterator<E> spliterator() {
		// TODO Auto-generated method stub
		return List.super.spliterator();
	}
}
