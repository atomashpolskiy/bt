/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
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
