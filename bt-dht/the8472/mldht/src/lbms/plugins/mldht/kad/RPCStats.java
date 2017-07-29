/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad;

import java.util.Formatter;

import lbms.plugins.mldht.kad.messages.MessageBase;
import lbms.plugins.mldht.kad.messages.MessageBase.Method;
import lbms.plugins.mldht.kad.messages.MessageBase.Type;

/**
 * @author Damokles
 *
 */
public class RPCStats {

	private long	receivedBytes;
	private long	sentBytes;

	private long	tmpReceivedBytes;
	private long	tmpSentBytes;
	private long	receivedBytesPerSec;
	private long	sentBytesPerSec;
	private long	tmpReceivedTimestamp;
	private long	tmpSentTimestamp;

	private long[][]	sentMessages;
	private long[][]	receivedMessages;
	private long[]	timeoutMessages;

	protected RPCStats () {
		sentMessages = new long[Method.values().length][Type.values().length];
		receivedMessages = new long[Method.values().length][Type.values().length];
		timeoutMessages = new long[Method.values().length];
	}
	
	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		
		@SuppressWarnings("resource")
		Formatter f = new Formatter(b);
		
		f.format("### local RPCs%n");
		f.format("%18s %19s | %19s %19s %19s %n%n", "Method", "REQ", "RSP", "Error", "Timeout");
		for(Method m : Method.values())
		{
			long sent = sentMessages[m.ordinal()][Type.REQ_MSG.ordinal()];
			long received = receivedMessages[m.ordinal()][Type.RSP_MSG.ordinal()];
			long error = receivedMessages[m.ordinal()][Type.ERR_MSG.ordinal()];
			long timeouts = timeoutMessages[m.ordinal()];
			f.format("%18s %19d | %19d %19d %19d %n", m, sent, received, error, timeouts);
		}
		f.format("%n### remote RPCs%n");
		f.format("%18s %19s | %19s %19s %n%n", "Method","REQ", "RSP", "Errors");
		for(Method m : Method.values())
		{
			long received = receivedMessages[m.ordinal()][Type.REQ_MSG.ordinal()];
			long sent = sentMessages[m.ordinal()][Type.RSP_MSG.ordinal()];
			long errors = sentMessages[m.ordinal()][Type.ERR_MSG.ordinal()];
			f.format("%18s %19d | %19d %19d %n", m, received, sent, errors);
		}
		
		return b.toString();
	}

	/**
	 * @return the receivedBytes
	 */
	public long getReceivedBytes () {
		return receivedBytes;
	}

	/**
	 * @return the sentBytes
	 */
	public long getSentBytes () {
		return sentBytes;
	}

	/**
	 * @return
	 */
	public long getReceivedBytesPerSec () {
		long now = System.currentTimeMillis();
		long d = now - tmpReceivedTimestamp;
		if (d > 950) {
			receivedBytesPerSec = (int) (tmpReceivedBytes * 1000 / d);
			tmpReceivedBytes = 0;
			tmpReceivedTimestamp = now;
		}
		return receivedBytesPerSec;
	}

	/**
	 * @return
	 */
	public long getSentBytesPerSec () {
		long now = System.currentTimeMillis();
		long d = now - tmpSentTimestamp;
		if (d > 950) {
			sentBytesPerSec = (int) (tmpSentBytes * 1000 / d);
			tmpSentBytes = 0;
			tmpSentTimestamp = now;
		}
		return sentBytesPerSec;
	}

	/**
	 * Returns the Count for the specified Message
	 *
	 * @param m The method of the message
	 * @param t The type of the message
	 * @return count
	 */
	public long getSentMessageCount (Method m, Type t) {
		return sentMessages[m.ordinal()][t.ordinal()];
	}

	/**
	 * Returns the Count for the specified Message
	 *
	 * @param m The method of the message
	 * @param t The type of the message
	 * @return count
	 */
	public long getReceivedMessageCount (Method m, Type t) {
		return receivedMessages[m.ordinal()][t.ordinal()];
	}

	/**
	 * Returns the Count for the specified requests
	 *
	 * @param m The method of the message
	 * @return count
	 */
	public long getTimeoutMessageCount (Method m) {
		return timeoutMessages[m.ordinal()];
	}

	/**
	 * @param receivedBytes the receivedBytes to add
	 */
	protected void addReceivedBytes (long receivedBytes) {
		tmpReceivedBytes += receivedBytes;
		this.receivedBytes += receivedBytes;
	}

	/**
	 * @param sentBytes the sentBytes to add
	 */
	protected void addSentBytes (long sentBytes) {
		tmpSentBytes += sentBytes;
		this.sentBytes += sentBytes;
	}

	protected void addSentMessageToCount (MessageBase msg) {
		sentMessages[msg.getMethod().ordinal()][msg.getType().ordinal()]++;
	}

	protected void addSentMessageToCount (Method m, Type t) {
		sentMessages[m.ordinal()][t.ordinal()]++;
	}

	protected void addReceivedMessageToCount (MessageBase msg) {
		receivedMessages[msg.getMethod().ordinal()][msg.getType().ordinal()]++;
	}

	protected void addReceivedMessageToCount (Method m, Type t) {
		receivedMessages[m.ordinal()][t.ordinal()]++;
	}

	protected void addTimeoutMessageToCount (MessageBase msg) {
		timeoutMessages[msg.getMethod().ordinal()]++;
	}
}
