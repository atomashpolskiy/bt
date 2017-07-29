/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.mldht.cli;

import java.util.List;
import java.util.Optional;

public class ParseArgs {
	
	public static boolean extractBool(List<String> args, String param) {
		return args.removeIf(param::equals);
	}
	
	public static Optional<String> extractString(List<String> args, String param) {
		int idx = args.indexOf(param);
		
		if(idx >= 0) {
			String val = args.remove(idx + 1);
			args.remove(idx);
			return Optional.of(val);
		}
		
		
		return Optional.empty();
	}

}
