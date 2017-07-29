/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad;

import lbms.plugins.mldht.kad.messages.MessageBase;

/**
 *  Class which objects should derive from, if they want to know the result of a call.
 *
 * @author Damokles
 */
public interface RPCCallListener {
	
	public default void stateTransition(RPCCall c, RPCState previous, RPCState current) {}

	/**
	 * A response was received.
	 * @param c The call
	 * @param rsp The response
	 */
	public default void onResponse (RPCCall c, MessageBase rsp) {}
	
	
	/**
	 * The call has not timed out yet but is estimated to be unlikely to succeed
	 */
	public default void onStall(RPCCall c) {}

	/**
	 * The call has timed out.
	 * @param c The call
	 */
	public default void onTimeout (RPCCall c) {}
}
