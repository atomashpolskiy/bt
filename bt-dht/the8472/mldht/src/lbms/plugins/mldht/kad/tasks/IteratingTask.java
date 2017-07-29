/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad.tasks;

import lbms.plugins.mldht.kad.DHT;
import lbms.plugins.mldht.kad.DHTConstants;
import lbms.plugins.mldht.kad.Key;
import lbms.plugins.mldht.kad.Node;
import lbms.plugins.mldht.kad.RPCServer;
import lbms.plugins.mldht.kad.RPCState;
import lbms.plugins.mldht.kad.tasks.IterativeLookupCandidates.LookupGraphNode;
import lbms.plugins.mldht.kad.utils.AddressUtils;
import lbms.plugins.mldht.kad.DHT.LogLevel;

import java.util.stream.Collectors;

public abstract class IteratingTask extends TargetedTask {
	
	ClosestSet closest;
	IterativeLookupCandidates todo;
	
	public IteratingTask(Key target, RPCServer srv, Node node) {
		super(target, srv, node);
		todo = new IterativeLookupCandidates(target, node.getDHT().getMismatchDetector());
		todo.setNonReachableCache(node.getDHT().getUnreachableCache());
		todo.setSpamThrottle(node.getDHT().getServerManager().getOutgoingRequestThrottle());
		closest = new ClosestSet(target, DHTConstants.MAX_ENTRIES_PER_BUCKET);
	}
	
	@Override
	public int getTodoCount() {
		return (int) todo.allCand().filter(todo.lookupFilter).count();
	}
	
	public String closestDebug() {
		return this.closest.entries().<String>map(kbe -> {
			Key k = kbe.getID();
			return k + "  " + targetKey.distance(k) + " src:" + todo.nodeForEntry(kbe).sources.size();
		}).collect(Collectors.joining("\n"));
	}
	
	protected void logClosest() {
		Key farthest = closest.tail();
		
		if(!DHT.isLogLevelEnabled(LogLevel.Verbose))
			return;
		
		DHT.log(this.toString() + "\n" +

				"Task "+ getTaskID() +"  done " + counts + " " + closest + "\n" + targetKey + "\n" + closestDebug()  + "\n" +
				

				todo.allCand().sorted(todo.comp()).filter(node -> {
					return targetKey.threeWayDistance(node.toKbe().getID(), farthest) <= 0;
				}).<String>map(node -> {

					
					return String.format("%s %s %s %s%s%s%s%s fail:%d src:%d call:%d rsp:%d acc:%d %s",
							node.toKbe().getID(),
							targetKey.distance(node.toKbe().getID()),
							AddressUtils.toString(node.toKbe().getAddress()),
							node.toKbe().hasSecureID() ? "🔒" : " ",
							node.root ? "🌲" : " ",
							node.tainted ? "!" : " ",
							node.throttled ? "⏳" : " ",
							node.unreachable ? "⛔" : " ",
							-node.previouslyFailedCount,
							node.sources.size(),
							node.calls.size(),
							node.calls.stream().filter(c -> c.state() == RPCState.RESPONDED).count(),
							node.acceptedResponse ? 1 : 0,
							node.sources.stream().map(LookupGraphNode::toKbe).collect(Collectors.toList())
						);

				}).collect(Collectors.joining("\n"))

				, LogLevel.Verbose);

	}

}
