/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad;

import java.time.Duration;
import java.time.Instant;

import lbms.plugins.mldht.kad.tasks.Task;

/**
 * @author Damokles
 *
 */
public class DHTStats {
	
	private static final double EMA_WEIGHT = 0.01;

	private DatabaseStats	dbStats;

	private RPCStats		rpcStats;

	private Instant			startedTimestamp;

	/// number of peers in the routing table
	private int				numPeers;
	/// Number of running tasks
	private int				numTasks;

	private long			numReceivedPackets;

	private long			numSentPackets;

	private int				numRpcCalls;
	
	private double			avgFirstResultTime = 10000;
	private double			avgFinishTime = 10000;

	/**
	 * @return the num_peers
	 */
	public int getNumPeers () {
		return numPeers;
	}

	/**
	 * @return the num_tasks
	 */
	public int getNumTasks () {
		return numTasks;
	}

	/**
	 * @return the num_received_packets
	 */
	public long getNumReceivedPackets () {
		return numReceivedPackets;
	}

	/**
	 * @return the num_sent_packets
	 */
	public long getNumSentPackets () {
		return numSentPackets;
	}

	/**
	 * @return the numRpcCalls
	 */
	public int getNumRpcCalls () {
		return numRpcCalls;
	}

	/**
	 * @return the dbStats
	 */
	public DatabaseStats getDbStats () {
		return dbStats;
	}

	/**
	 * @return the rpcStats
	 */
	public RPCStats getRpcStats () {
		return rpcStats;
	}

	/**
	 * @return the startedTimestamp
	 */
	public Instant getStartedTimestamp () {
		return startedTimestamp;
	}

	/**
	 * @param num_peers the num_peers to set
	 */
	protected void setNumPeers (int num_peers) {
		this.numPeers = num_peers;
	}

	/**
	 * @param num_tasks the num_tasks to set
	 */
	protected void setNumTasks (int num_tasks) {
		this.numTasks = num_tasks;
	}
	
	public void taskFinished(Task t)
	{
		if(t.getFinishedTime() <= 0)
			return;
		avgFinishTime = (t.getFinishedTime() - t.getStartTime()) * EMA_WEIGHT + avgFinishTime * (1.0 - EMA_WEIGHT);
		//System.out.println("fin "+(t.getFinishedTime() - t.getStartTime()));
		if(t.getFirstResultTime() <= 0)
			return;
		avgFirstResultTime = (t.getFirstResultTime() - t.getStartTime()) * EMA_WEIGHT + avgFirstResultTime * (1.0 - EMA_WEIGHT);
		//System.out.println("1st "+(t.getFirstResultTime() - t.getStartTime()));
	}

	/**
	 * @param num_received_packets the num_received_packets to set
	 */
	protected void setNumReceivedPackets (long num_received_packets) {
		this.numReceivedPackets = num_received_packets;
	}

	/**
	 * @param num_sent_packets the num_sent_packets to set
	 */
	protected void setNumSentPackets (long num_sent_packets) {
		this.numSentPackets = num_sent_packets;
	}

	/**
	 * @param numRpcCalls the numRpcCalls to set
	 */
	protected void setNumRpcCalls (int numRpcCalls) {
		this.numRpcCalls = numRpcCalls;
	}

	/**
	 * @param dbStats the dbStats to set
	 */
	protected void setDbStats (DatabaseStats dbStats) {
		this.dbStats = dbStats;
	}

	/**
	 * @param rpcStats the rpcStats to set
	 */
	protected void setRpcStats (RPCStats rpcStats) {
		this.rpcStats = rpcStats;
	}

	protected void resetStartedTimestamp () {
		startedTimestamp = Instant.now();
	}
	
	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		b.append("DB Keys: ").append(dbStats.getKeyCount()).append('\n');
		b.append("DB Items: ").append(dbStats.getItemCount()).append('\n');
		b.append("TX sum: ").append(numSentPackets).append(" RX sum: ").append(numReceivedPackets).append('\n');
		b.append("avg task time/avg 1st result time (ms): ").append((int)avgFinishTime).append('/').append((int)avgFirstResultTime).append('\n');
		b.append("Uptime: ").append(Duration.between(startedTimestamp, Instant.now())).append("s\n");
		b.append("RPC stats\n");
		b.append(rpcStats.toString());
		return b.toString();
	}
}
