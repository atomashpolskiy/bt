/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.utils;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;


public interface Selectable {
	public SelectableChannel getChannel();

	public void registrationEvent(NIOConnectionManager manager, SelectionKey key) throws IOException;
	
	public void selectionEvent(SelectionKey key) throws IOException;

	public void doStateChecks(long now) throws IOException;

	public int calcInterestOps();
}
