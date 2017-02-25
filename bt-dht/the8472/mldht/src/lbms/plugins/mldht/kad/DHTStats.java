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
