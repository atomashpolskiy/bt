/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.mldht;

import java.util.Collection;

import lbms.plugins.mldht.kad.DHT;
import the8472.utils.ConfigReader;

public interface Component {
	
	void start(Collection<DHT> dhts, ConfigReader config);
	
	void stop();

}
