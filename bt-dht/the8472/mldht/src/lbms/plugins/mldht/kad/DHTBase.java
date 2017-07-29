/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad;

import java.net.SocketException;

import lbms.plugins.mldht.DHTConfiguration;
import lbms.plugins.mldht.kad.messages.AbstractLookupRequest;
import lbms.plugins.mldht.kad.messages.AnnounceRequest;
import lbms.plugins.mldht.kad.messages.ErrorMessage;
import lbms.plugins.mldht.kad.messages.GetPeersRequest;
import lbms.plugins.mldht.kad.messages.MessageBase;
import lbms.plugins.mldht.kad.messages.PingRequest;
import lbms.plugins.mldht.kad.tasks.AnnounceTask;
import lbms.plugins.mldht.kad.tasks.PeerLookupTask;
import lbms.plugins.mldht.kad.tasks.TaskManager;

/**
 * @author Damokles
 *
 */
public interface DHTBase {
	/**
	 * Start the DHT
	 */
	void start (DHTConfiguration config) throws SocketException;

	/**
	 * Stop the DHT
	 */
	void stop ();

	/**
	 * Update the DHT
	 */
	void update ();

	/**
	 * Do an announce/scrape lookup on the DHT network
	 * @param info_hash The info_hash
	 * @return The task which handles this
	 */
	public PeerLookupTask createPeerLookup(byte[] info_hash);
	
	
	/**
	 * Perform the put() operation for an announce
	 */
	public AnnounceTask announce(PeerLookupTask lookup, boolean isSeed, int btPort);

	/**
	 * See if the DHT is running.
	 */
	boolean isRunning ();

	/// Get statistics about the DHT
	DHTStats getStats ();

	/**
	 * Add a DHT node. This node shall be pinged immediately.
	 * @param host The hostname or ip
	 * @param hport The port of the host
	 */
	void addDHTNode (String host, int hport);

	void started ();

	void stopped ();

	public void ping (PingRequest r);

	public void findNode (AbstractLookupRequest r);

	public void response (MessageBase r);

	public void getPeers (GetPeersRequest r);

	public void announce (AnnounceRequest r);

	public void error (ErrorMessage r);

	public void timeout (RPCCall r);

	public void addStatsListener (DHTStatsListener listener);

	public void removeStatsListener (DHTStatsListener listener);

	public Node getNode ();

	public TaskManager getTaskManager ();

	Key getOurID ();
}
