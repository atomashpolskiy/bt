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
package lbms.plugins.mldht.kad.utils;

import java.util.Arrays;

public final class ByteWrapper {
	public final byte[] arr;
	private final int hash;
	
	public ByteWrapper(byte[] a)
	{
		arr = a;
		hash = Arrays.hashCode(a);
	}
	
	@Override
	public int hashCode() {
		return hash;
	}
	
	@Override
	public boolean equals(Object obj) {
		return obj instanceof ByteWrapper && Arrays.equals(arr,((ByteWrapper)obj).arr);
	}
}