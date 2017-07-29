/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.bt;

import lbms.plugins.mldht.indexer.utils.RotatingBloomFilter;
import lbms.plugins.mldht.kad.utils.AddressUtils;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

public class UselessPeerFilter {
	
	private static long SHORT_TIMEOUT = TimeUnit.MINUTES.toMillis(10);
	private static long LONG_TIMEOUT = TimeUnit.HOURS.toMillis(2);
	
	RotatingBloomFilter shortTimeoutFilter = new RotatingBloomFilter(1000_000, 1024*1024);
	RotatingBloomFilter longTimeoutFilter = new RotatingBloomFilter(1000_000, 1024*1024);
	long lastLongFilterRotation;
	long lastShortFilterRotation;
	
	public UselessPeerFilter() {
		long now = System.currentTimeMillis();
		lastShortFilterRotation = lastLongFilterRotation = now;
	}
	
	public void insert(PullMetaDataConnection toAdd) {
		if(toAdd.closeReason == null) {
			throw new IllegalArgumentException("peer connection not closed yet");
		}
		
		ByteBuffer data = ByteBuffer.wrap(AddressUtils.packAddress(toAdd.remoteAddress));

		synchronized (this) {
			switch(toAdd.closeReason) {
				case CONNECT_FAILED:
					shortTimeoutFilter.insert(data);
					break;
				case NO_LTEP:
				case NO_META_EXCHANGE:
					longTimeoutFilter.insert(data);
					break;
				default:
					return;
			}
		}
		
	}
	
	public boolean isBad(InetSocketAddress addr) {
		ByteBuffer data = ByteBuffer.wrap(AddressUtils.packAddress(addr));
		synchronized (this) {
			return shortTimeoutFilter.contains(data) || longTimeoutFilter.contains(data) ;
		}
	}
	
	public void clean()  {
		long now = System.currentTimeMillis();

		synchronized (this) {
			if(now - lastLongFilterRotation > LONG_TIMEOUT) {
				longTimeoutFilter.rotate();
				lastLongFilterRotation = now;
			}

			if(now - lastShortFilterRotation > SHORT_TIMEOUT) {
				shortTimeoutFilter.rotate();
				lastShortFilterRotation = now;
			}
		}
		
	}

}
