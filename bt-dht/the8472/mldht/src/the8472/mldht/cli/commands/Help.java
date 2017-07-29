/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.mldht.cli.commands;

import the8472.mldht.cli.CommandProcessor;

public class Help extends CommandProcessor {

	@Override
	protected void process() {
		println("HELP                                                 - prints this help");
		println("PING ip port | hex                                   - continuously pings a DHT node with a 1 second interval");
		println("GET hash [salt]                                      - perform a BEP44 get");
		println("PUT -f <input-path> [-keyfile <path>] [-salt <salt>]");
		println("PUT <input> [-keyfile <path>] [-salt <salt>]         - perform a BEP44 put, specifying a salt or keyfile implies a mutable put, immutable otherwise. data will be read from file or as single argument");
		println("GETTORRENT [infohash...]                             - peer lookup for <infohash(es)>, then attempt metadata exchange, then write .torrent file(s) to the current working directory");
		println("GETPEERS [infohash...] [-fast] [-nocache]            - peer lookup for <infohash(es)>, print ip address/port tuples");
		println("BURST [count]                                        - run a batch of find_node lookups to random target IDs. intended test the attainable throughput for active lookups, subject to internal throttling");
		println("SAMPLE                                        			");
		exit(0);
	}

}
