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
package lbms.plugins.mldht.kad;

import static the8472.utils.Arrays.compareUnsigned;

import java.util.Arrays;
import java.util.Comparator;

import the8472.bencode.Utils;

/**
 * @author Damokles
 *
 */
public class DBItem implements Comparable<DBItem> {

	protected byte[] item;
	private final long	time_stamp;

	private DBItem () {
		time_stamp = System.currentTimeMillis();
	}

	public DBItem (final byte[] ip_port) {
		this();
		item = ip_port.clone();
	}

	/// See if the item is expired
	public boolean expired (final long now) {
		return (now - time_stamp >= DHTConstants.MAX_ITEM_AGE);
	}
	
	public long getCreatedAt() {
		return time_stamp;
	}

	/// Get the data of an item
	public byte[] getData () {
		return item;
	}

	@Override
	public String toString() {
		return "DBItem: "+Utils.prettyPrint(item);
	}
	
	// sort by raw data. only really useful for binary search
	public int compareTo(DBItem other) {
		return compareUnsigned(item, other.item);
	}

	@Override
	public boolean equals(final Object obj) {
		if(obj instanceof DBItem)
		{
			byte[] otherItem = ((DBItem)obj).item;
			return Arrays.equals(item, otherItem);
		}
		return false;
	}
	
	@Override
	public int hashCode() {
		return Arrays.hashCode(item);
	}

	public static final Comparator<DBItem> ageOrdering = (o1, o2) -> (int)(o1.time_stamp - o2.time_stamp);
}
