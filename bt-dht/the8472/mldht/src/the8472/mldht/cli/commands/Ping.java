/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.mldht.cli.commands;

import static the8472.bencode.Utils.hex2ary;

import the8472.bencode.Utils;
import the8472.mldht.cli.CommandProcessor;

import lbms.plugins.mldht.kad.DHT;
import lbms.plugins.mldht.kad.RPCCall;
import lbms.plugins.mldht.kad.RPCCallListener;
import lbms.plugins.mldht.kad.RPCServer;
import lbms.plugins.mldht.kad.messages.MessageBase;
import lbms.plugins.mldht.kad.messages.PingRequest;
import lbms.plugins.mldht.kad.utils.AddressUtils;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Ping extends CommandProcessor {
	
	InetSocketAddress target;
	
	ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1);
	
	{
		timer.setKeepAliveTime(2, TimeUnit.SECONDS);
		timer.allowCoreThreadTimeOut(true);
	}
	
	AtomicInteger iteration = new AtomicInteger();
		
	
	@Override
	protected void process() {
		InetAddress addr;
		int port;
		
		try {
			String ip = arguments.get(0);
			
			Matcher m = Pattern.compile("^(?:0x)?(\\p{XDigit}+)$").matcher(ip);
			
			if(m.matches()) {
				String hex = m.group(1);
				byte[] raw = hex2ary(hex);
				InetSocketAddress sockaddr = AddressUtils.unpackAddress(raw);
				addr = sockaddr.getAddress();
				port = sockaddr.getPort();
			} else {
				port = Integer.valueOf(arguments.get(1));
				addr = InetAddress.getByName(ip);
			}
			
		} catch (Exception e) {
			handleException(e);
			return;
		}
		
		target = new InetSocketAddress(addr, port);
		
		println("PING " + target);
		
		doPing();
	}
	
	void doPing() {
		if(!isRunning())
			return;
		
		Optional<DHT> dht = dhts.stream().filter(d -> d.getType().PREFERRED_ADDRESS_TYPE == target.getAddress().getClass()).findAny();
		
		
		if(!dht.isPresent()) {
			printErr("no dht with an address type matching " + target.getAddress() + " found");
			exit(1);
		}
		
		
		
		RPCServer srv = dht.get().getServerManager().getRandomActiveServer(true);
		PingRequest req = new PingRequest();
		req.setDestination(target);
		RPCCall call = new RPCCall(req);
		call.addListener(new RPCCallListener() {
			
			int counter = iteration.incrementAndGet();
			
			@Override
			public void onTimeout(RPCCall c) {
				println("#"+counter+": timed out");
				timer.schedule(Ping.this::doPing, 1, TimeUnit.SECONDS);
			}
			
			@Override
			public void onStall(RPCCall c) {}
			
			@Override
			public void onResponse(RPCCall c, MessageBase rsp) {
				println("#"+counter+" response time=" + c.getRTT() + "ms " + rsp.getID() + rsp.getVersion().map(v -> " ver:" + Utils.prettyPrint(v)).orElse(""));
				timer.schedule(Ping.this::doPing, 1, TimeUnit.SECONDS);
				
			}
		});
		srv.doCall(call);
	}

}
