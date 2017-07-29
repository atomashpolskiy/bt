/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.mldht.indexing;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Collection;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TransferQueue;

import lbms.plugins.mldht.kad.DHT;
import lbms.plugins.mldht.kad.messages.GetPeersRequest;
import lbms.plugins.mldht.kad.messages.MessageBase;
import lbms.plugins.mldht.kad.messages.MessageBase.Method;
import the8472.mldht.Component;
import the8472.utils.ConfigReader;

public class OpentrackerLiveSync implements Component {
	
	TransferQueue<ByteBuffer> toSend = new LinkedTransferQueue<>();
	DatagramChannel channel;
	
	byte[] id = new byte[4];
	Thread t = new Thread(this::send);
	volatile boolean running = true;
	
	private static final int HEADER_LENGTH = 0x08;
	private static final int PEER_LENGTH = 0x1C;
	private static final int PEERS_PER_PACKET = 50;
	
	public OpentrackerLiveSync() {
		ThreadLocalRandom.current().nextBytes(id);
	}
	
	@Override
	public void start(Collection<DHT> dhts, ConfigReader config) {
		try {
			channel = DatagramChannel.open(StandardProtocolFamily.INET);
			channel.setOption(StandardSocketOptions.IP_MULTICAST_TTL, 1);
			channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
			// we only need to send, not to receive, so need to bind to a specific port
			channel.bind(new InetSocketAddress(0));
			channel.connect(new InetSocketAddress(InetAddress.getByAddress(new byte[] {(byte) 224,0,23,5}), 9696));
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		
		t.setDaemon(true);
		t.setName("opentracker-sync");
		t.start();
		
		// OT-sync only supports ipv4 atm
		dhts.stream().filter(d -> d.getType().PREFERRED_ADDRESS_TYPE == Inet4Address.class).forEach(d -> {
			d.addIncomingMessageListener(this::incomingPacket);
		});

	}
	
	void incomingPacket(DHT dht, MessageBase msg) {
		if(!running)
			return;
		if(msg.getType() != MessageBase.Type.REQ_MSG || msg.getMethod() != Method.GET_PEERS)
			return;
		
		GetPeersRequest req = (GetPeersRequest) msg;
		
		ByteBuffer buf = ByteBuffer.allocate(PEER_LENGTH);
		
		buf.put(req.getTarget().getHash());
		buf.put(req.getOrigin().getAddress().getAddress());
		buf.putShort((short) req.getOrigin().getPort());
		buf.putShort((short) 0);
		buf.flip();
		toSend.add(buf);
	}
	
	
	
	void send() {
		ByteBuffer sendBuffer = ByteBuffer.allocate(HEADER_LENGTH);
		sendBuffer.put(id);
		sendBuffer.put(new byte[4]);
		sendBuffer.flip();
		
		ByteBuffer[] buffers = new ByteBuffer[1 + PEERS_PER_PACKET];
		buffers[0] = sendBuffer;

		try {
			while(running) {
				for(int i = 1;i<buffers.length;i++) {
					buffers[i] = toSend.take();
				}
				
				channel.write(buffers);
				
				buffers[0].rewind();
				
			}

		} catch (IOException | InterruptedException e) {
			running = false;
			e.printStackTrace();
		}
		
				
	}

	@Override
	public void stop() {
		running = false;
		try {
			channel.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

}


// === OT format
// [mcast address seems to be outdated]

/*
Syncing is done as udp packets in the multicast domain 224.0.42.5 port 9696

Each tracker should join the multicast group and send its live sync packets
to that group, using a ttl of 1

Format of all sync packets is straight forward, packet type determines
which kind of packet this is:

  0x0000 0x04 id of tracker instance
  0x0004 0x04 packet type

                           ########
######## PEER SYNC PROTOCOL ########
########

Each tracker instance accumulates announce requests until its buffer is
full or a timeout is reached. Then it broadcasts its live sync packer:

packet type SYNC_LIVE
[ 0x0008 0x14 info_hash
  0x001c 0x04 peer's ipv4 address
  0x0020 0x02 peer's port
  0x0024 0x02 peer flags v1 ( SEEDING = 0x80, COMPLETE = 0x40, STOPPED = 0x20 )
]*

*/
