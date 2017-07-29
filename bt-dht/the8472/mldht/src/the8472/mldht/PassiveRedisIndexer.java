/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.mldht;

import static the8472.bencode.Utils.str2buf;
import static the8472.utils.Functional.tap;
import static the8472.utils.Functional.unchecked;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

import lbms.plugins.mldht.kad.DHT;
import lbms.plugins.mldht.kad.Key;
import lbms.plugins.mldht.kad.messages.GetPeersRequest;
import lbms.plugins.mldht.kad.messages.MessageBase;
import lbms.plugins.mldht.kad.messages.MessageBase.Method;
import lbms.plugins.mldht.kad.messages.MessageBase.Type;
import lbms.plugins.mldht.utils.NIOConnectionManager;
import lbms.plugins.mldht.utils.Selectable;
import the8472.utils.ConfigReader;
import the8472.utils.XMLUtils;
import the8472.utils.concurrent.SerializedTaskExecutor;

public class PassiveRedisIndexer implements Component {
	
	private Queue<ByteBuffer> writeQueue = new ConcurrentLinkedQueue<>();
	
	private volatile boolean running = true;
	
	ConfigReader config;
	
	AtomicReference<SocketHandler> ref = new AtomicReference<>();
	
	NIOConnectionManager conMan;
	
	public void start(Collection<DHT> dhts, ConfigReader config)  {
		this.config = config;
		
		conMan = new NIOConnectionManager("redis selector");
		
		dhts.forEach((dht) -> {
			dht.addIncomingMessageListener(this::incomingMessage);
		});
		
	}
	
	
	SocketHandler ensureOpen() {
		SocketHandler handler = ref.get();
		if(handler == null) {
			handler = new SocketHandler();
			if(ref.compareAndSet(null, handler)) {
				handler.open();
			}
		}
		
		return handler;
	}
	
	public void stop() {
		running = false;
	}
	
	private static final String TTL = Integer.toString(2*24*3600);
	
	private void incomingMessage(DHT dht, MessageBase msg) {
		if(!running)
			return;
		
		if(msg.getType() == Type.REQ_MSG && msg.getMethod() == Method.GET_PEERS)
		{
			GetPeersRequest req = (GetPeersRequest) msg;
			long now = System.currentTimeMillis();
			Key k =	req.getTarget();
			String ipAddr = req.getOrigin().getAddress().getHostAddress();
			String key = k.toString(false);
			
			StringBuilder b = new StringBuilder();
			
			
			
			// zadd <hash> <timestamp> <ip>
			b.append("*4\r\n");

			b.append("$4\r\n");
			b.append("ZADD\r\n");

			b.append("$40\r\n");
			b.append(key).append("\r\n");
			
			String intAsString = Long.toString(now);
			b.append('$').append(intAsString.length()).append("\r\n");
			b.append(intAsString).append("\r\n");

			b.append('$').append(ipAddr.length()).append("\r\n");
			b.append(ipAddr).append("\r\n");
			
			// expire <hash> <ttl>
			
			b.append("*3\r\n");
			
			b.append("$6\r\n");
			b.append("EXPIRE\r\n");

			b.append("$40\r\n");
			b.append(key).append("\r\n");
			
			b.append('$').append(TTL.length()).append("\r\n");
			b.append(TTL).append("\r\n");
			
			SocketHandler handler = ensureOpen();

			// to avoid OOM we only fill the queue when we have an open connection
			if(handler.getChannel() != null && handler.getChannel().isConnected()) {
				writeQueue.add(str2buf(b.toString()));
				handler.tryWrite.run();
			}
			
						
		}
	}
	
	
	static private final Map<String,String> namespaces = tap(new HashMap<>(), m -> m.put("xsi","http://www.w3.org/2001/XMLSchema-instance"));
	
	private InetAddress getAddress() {
		return config.get(XMLUtils.buildXPath("//components/component[@xsi:type='mldht:redisIndexerType']/address",namespaces)).flatMap(unchecked(str -> Optional.of(InetAddress.getByName(str)))).get();
	}

	class SocketHandler implements Selectable {
		
		SocketChannel chan;
		
		void open() {
			try {
				chan = SocketChannel.open();
				chan.configureBlocking(false);
				chan.connect(new InetSocketAddress(getAddress(),6379));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			conMan.register(this);
			
		}
		
		void close() {
			writeQueue.clear();
			ref.compareAndSet(this, null);
			try {
				chan.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}
	
		@Override
		public SocketChannel getChannel() {
			return chan;
		}
	
		@Override
		public void registrationEvent(NIOConnectionManager manager, SelectionKey key) throws IOException {
			
			// TODO Auto-generated method stub
			
		}
		
		
	
		@Override
		public void selectionEvent(SelectionKey key) throws IOException {
			if(key.isValid() && key.isConnectable()) {
				chan.finishConnect();
				conMan.interestOpsChanged(this);
			}
				
			if(key.isValid() && key.isReadable())
				read();
			if(key.isValid() && key.isWritable()) {
				awaitingWriteNotification = false;
				tryWrite.run();
				conMan.interestOpsChanged(this);
			}
				
			
		}
		
		volatile boolean awaitingWriteNotification = true;
		ByteBuffer toWrite;
		
		Runnable tryWrite = SerializedTaskExecutor.onceMore(() -> {
			while(!awaitingWriteNotification && !writeQueue.isEmpty()) {
				if(toWrite == null)
					toWrite = writeQueue.poll();
				if(toWrite == null)
					continue;
				
				int written = 0;
				try {
					chan.write(toWrite);
				} catch (IOException e) {
					e.printStackTrace();
				}
				
				if(written < 0) {
					awaitingWriteNotification = true;
					close();
					continue;
				}
				
				if(toWrite.remaining() > 0) {
					awaitingWriteNotification = true;
					conMan.interestOpsChanged(this);
				} else {
					toWrite = null;
				}
			}
		});
		
		ByteBuffer oblivion = ByteBuffer.allocateDirect(4*1024);
		
		void read() throws IOException {
			while(true) {
				oblivion.clear();
				int read = chan.read(oblivion);
				if(read < 0)
					close();
				if(read <= 0)
					break;
			}
		}
	
		@Override
		public void doStateChecks(long now) throws IOException {
			if(!chan.isOpen()) {
				close();
				conMan.deRegister(this);
			}
				
		}
	
		@Override
		public int calcInterestOps() {
			int ops = SelectionKey.OP_READ;
			
			if(chan.isConnectionPending())
				ops |= SelectionKey.OP_CONNECT;
			
			if(awaitingWriteNotification)
				ops |= SelectionKey.OP_WRITE;
				
			return ops;
		}
		
	}

}
