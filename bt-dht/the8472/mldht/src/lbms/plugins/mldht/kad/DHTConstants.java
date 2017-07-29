/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * @author Damokles
 *
 */
public class DHTConstants {

	public static final int		DHT_UPDATE_INTERVAL						= 1000;
	public static final int		MAX_ENTRIES_PER_BUCKET					= 8;
	public static final int		MAX_ACTIVE_TASKS						= 7;
	public static final int		MAX_ACTIVE_CALLS						= 256;
	public static final int		MAX_PENDING_ENTRIES_PER_BUCKET			= 5;
	public static final int		BUCKET_REFRESH_INTERVAL					= 15 * 60 * 1000;
	public static final int		MAX_CONCURRENT_REQUESTS					= 10;
	public static final	int		MAX_CONCURRENT_REQUESTS_LOWPRIO			= 3;
	public static final int		RECEIVE_BUFFER_SIZE						= 5 * 1024;

	public static final int		CHECK_FOR_EXPIRED_ENTRIES				= 5 * 60 * 1000;
	public static final int		MAX_ITEM_AGE							= 60 * 60 * 1000;
	public static final int		TOKEN_TIMEOUT							= 5 * 60 * 1000;
	
	public static final int		RPC_CALL_TIMEOUT_MAX					= 10 * 1000;
	public static final int		RPC_CALL_TIMEOUT_BASELINE_MIN			= 100; // ms
	public static final int		TASK_TIMEOUT							= 2 * 60 * 1000;
	
	public static final int		MAX_DB_ENTRIES_PER_KEY					= 6000;

	// enter survival mode if we don't see new packets after this time
	public static final int		REACHABILITY_TIMEOUT					= 60 * 1000;
	// remain in survival mode after we've recieved new packets for this time
	public static final int		REACHABILITY_RECOVERY					= 2 * 60 * 1000;

	public static final long	KBE_QUESTIONABLE_TIME					= 15 * 60 * 1000;
	public static final int		KBE_BAD_IF_FAILED_QUERIES_LARGER_THAN	= 2;
	public static final int		KBE_BAD_IMMEDIATLY_ON_FAILED_QUERIES	= 8;
	public static final int		KBE_QUESTIONABLE_TIME_PING_MULTIPLIER	= 4;

	public static final	int		BOOTSTRAP_MIN_INTERVAL					= 4 * 60 * 1000;
	public static final int		BOOTSTRAP_IF_LESS_THAN_X_PEERS			= 30;
	public static final int		USE_BT_ROUTER_IF_LESS_THAN_X_PEERS		= 10;

	public static final int		DEFAULT_WANTED_NODE_RESPONSES_ON_NL		= MAX_ENTRIES_PER_BUCKET * 4;

	public static final int		SELF_LOOKUP_INTERVAL					= 30 * 60 * 1000;
	public static final int		RANDOM_LOOKUP_INTERVAL					= 10 * 60 * 1000;

	public static final int		ANNOUNCE_CACHE_MAX_AGE					= 30 * 60 * 1000;
	public static final int		ANNOUNCE_CACHE_FAST_LOOKUP_AGE			= 8 * 60 * 1000;


	public static final InetSocketAddress[] UNRESOLVED_BOOTSTRAP_NODES = new InetSocketAddress[] {
			InetSocketAddress.createUnresolved("dht.transmissionbt.com", 6881),
			InetSocketAddress.createUnresolved("router.bittorrent.com", 6881),
			InetSocketAddress.createUnresolved("router.utorrent.com", 6881),
			InetSocketAddress.createUnresolved("router.silotis.us", 6881),
	};
	private static String version = null;

	public static String getVersion() {
		return version;
	}

	public static void setVersion (int ver) {
		version = "ml" + new String(new byte[] { (byte) (ver >> 8 & 0xFF) , (byte) (ver & 0xff) }, StandardCharsets.ISO_8859_1);
	}
	
	static {
		// 0.1.1
		setVersion(11);
	}

}
