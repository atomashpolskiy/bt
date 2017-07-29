/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad.messages;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lbms.plugins.mldht.kad.DHT;

/**
 * @author Damokles
 *
 */
public class ErrorMessage extends MessageBase {

	private String	msg;
	private int		code;

	/**
	 * @param mtid
	 * @param id
	 * @param msg
	 */
	public ErrorMessage (byte[] mtid, int code, String msg) {
		super(mtid, Method.UNKNOWN, Type.ERR_MSG);
		this.msg = msg;
		this.code = code;
	}

	/* (non-Javadoc)
	 * @see lbms.plugins.mldht.kad.messages.MessageBase#apply(lbms.plugins.mldht.kad.DHT)
	 */
	@Override
	public void apply (DHT dh_table) {
		dh_table.error(this);
	}
	
	@Override
	public Map<String, Object> getBase() {
		Map<String, Object> base = super.getBase();
		List<Object> errorDetails = new ArrayList<>(2);
		errorDetails.add(code);
		errorDetails.add(msg);
		base.put(getType().innerKey(), errorDetails);
		
		return base;
	}
	
	public void setMethod(Method m) {
		this.method = m;
	}



	/**
	 * @return the Message
	 */
	public String getMessage () {
		return msg;
	}

	/**
	 * @return the code
	 */
	public int getCode () {
		return code;
	}

	public static enum ErrorCode {
		GenericError(201),
		ServerError(202),
		ProtocolError(203), //such as a malformed packet, invalid arguments, or bad token
		MethodUnknown(204),

		/*
		BEP44:
		205	message (v field) too big.
		206	invalid signature
		207	salt (salt field) too big.
		301	the CAS hash mismatched, re-read value and try again.
		302	sequence number less than current.
		*/
		PutMessageTooBig(205),
		InvalidSignature(206),
		SaltTooBig(207),
		CasFail(301),
		CasNotMonotonic(302);
		
		

		public final int code;

        private ErrorCode(int code) {
        	this.code = code;
        }
	}
	
	@Override
	public String toString() {
		return super.toString() + " code:"+ code + " errormsg: '"+msg+"'" + " id:" + getID();
	}
}
