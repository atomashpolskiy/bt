/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht;

import java.nio.file.Path;

public interface DHTConfiguration {
	
	public boolean isPersistingID();

	public Path getStoragePath();

	public int getListeningPort();
	
	public boolean noRouterBootstrap();
	
	public boolean allowMultiHoming();
	
	
}
