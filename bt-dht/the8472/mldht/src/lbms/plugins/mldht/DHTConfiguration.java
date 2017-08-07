/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht;

import java.net.InetAddress;
import java.nio.file.Path;
import java.util.function.Predicate;

public interface DHTConfiguration {
	
	public boolean isPersistingID();

	public Path getStoragePath();

	public int getListeningPort();
	
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
	 * A predicate that allows any the <em>any local address</em> of a particular address family is considered to allow all addresses of that family
	 * 
	 * The default implementation does not apply any restrictions.
	 * 
	 * The predicate may be be evaluated frequently, implementations should be approximately constant-time.
	 */
	public default Predicate<InetAddress> filterBindAddress() {
		return (unused) -> true;
	}
}
