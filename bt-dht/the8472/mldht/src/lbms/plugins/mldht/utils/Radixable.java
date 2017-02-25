package lbms.plugins.mldht.utils;

public interface Radixable<T> extends Comparable<T> {
	public int getRadix(int byteIndex);
}