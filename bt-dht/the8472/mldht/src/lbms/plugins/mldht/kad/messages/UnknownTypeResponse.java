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
package lbms.plugins.mldht.kad.messages;

import lbms.plugins.mldht.kad.DHT;

/**
 * @author Damokles
 *
 */
public class UnknownTypeResponse extends AbstractLookupResponse {
	public UnknownTypeResponse (byte[] mtid) {
		super(mtid, Method.UNKNOWN, Type.RSP_MSG);
	}

	@Override
	public void apply (DHT dh_table) {
		throw new UnsupportedOperationException("incoming, unknown responses cannot be applied, they may only exist to send error messages");
	}
}
