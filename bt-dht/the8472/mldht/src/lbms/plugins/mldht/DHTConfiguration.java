/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht;

import lbms.plugins.mldht.kad.DHT;

import java.net.InetAddress;
import java.nio.file.Path;
import java.util.function.Predicate;

public interface DHTConfiguration {
	
	/**
	 * if true and combined with an existing storage directory the base ID from which individual RPCServer IDs are derived will be returned
	 */
	public boolean isPersistingID();

	/**
	 * If a Path that points to an existing, writable directory is returned then the routing table
	 * will be persisted to that directory periodically and during shutdown
	 */
	public Path getStoragePath();

	public int getListeningPort();
	
	/**
	 * if true then no attempt to bootstrap through well-known nodes is made.
	 * you either must have a persisted routing table which can be loaded or
	 * manually seed the routing table by calling {@link DHT#addDHTNode(String, int)}
	 */
	public boolean noRouterBootstrap();

	/**
	 * If true one DHT node per globally routable unicast address will be used. Recommended for IPv6 nodes or servers-class machines directly connected to the internet.<br>
	 * If false only one node will be bound. Usually to the the default route. Recommended for IPv4 nodes.
	 */
	public boolean allowMultiHoming();
	
	/**
	 * A DHT node will automatically select socket bind addresses based on internal policies from available addresses,
	 * the predicate can be used to limit this selection to a subset.
	 * 
	 * A predicate that allows the <em>any local address</em> of a particular address family is considered to allow all addresses <em>of that family</em>
	 * 
	 * The default implementation does not apply any restrictions.
	 * 
	 * The predicate may be be evaluated frequently, implementations should be approximately constant-time.
	 */
	public default Predicate<InetAddress> filterBindAddress() {
		return (unused) -> true;
	}
}
