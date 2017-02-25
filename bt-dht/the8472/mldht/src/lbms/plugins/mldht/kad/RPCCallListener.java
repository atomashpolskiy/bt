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
