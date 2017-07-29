/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.mldht;

import static the8472.bencode.Utils.str2buf;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

import lbms.plugins.mldht.kad.DHT;
import lbms.plugins.mldht.kad.Key;
import lbms.plugins.mldht.kad.messages.GetPeersRequest;
import lbms.plugins.mldht.kad.messages.MessageBase;
import lbms.plugins.mldht.utils.NIOConnectionManager;
import lbms.plugins.mldht.utils.Selectable;
import the8472.utils.ConfigReader;

public class Firehose implements Component {
	
	List<Connection> connections = new CopyOnWriteArrayList<>();
	
	@Override
	public void start(Collection<DHT> dhts, ConfigReader config) {
		selector = new NIOConnectionManager("firehose");
		dhts.forEach(d -> {
			d.addIncomingMessageListener(this::incomingMessage);
		});
		
		try {
			selector.register(new Server());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	NIOConnectionManager selector;
	
	class Server implements Selectable {
		
		public Server() throws IOException {
			chan = ServerSocketChannel.open();
			chan.configureBlocking(false);
			// listen on [::0]:35465
			chan.bind(new InetSocketAddress(InetAddress.getByAddress(new byte[16]), 35465));
		}
		
		ServerSocketChannel chan ;

		@Override
		public SelectableChannel getChannel() {
			return chan;
		}

		@Override
		public void registrationEvent(NIOConnectionManager manager, SelectionKey key) throws IOException {}

		@Override
		public void selectionEvent(SelectionKey key) throws IOException {
			SocketChannel connection;
			while((connection = chan.accept()) != null) {
				new Connection(connection);
			}
		}

		@Override
		public void doStateChecks(long now) throws IOException {}

		@Override
		public int calcInterestOps() {
			return SelectionKey.OP_ACCEPT;
		}
		
	};
	
	class Connection implements Selectable {
		
		final SocketChannel chan;
		
		AtomicInteger writePointer = new AtomicInteger();
		AtomicInteger readPointer = new AtomicInteger();
		// length must be power of 2
		AtomicReferenceArray<ByteBuffer> toWrite = new AtomicReferenceArray<>(1024);
		volatile boolean empty = true;
		
		
		void add(ByteBuffer buf) {
			int current = writePointer.getAndIncrement();
			toWrite.set(current & (toWrite.length() - 1), buf);
			if(empty) {
				readPointer.set(current);
				empty = false;
				selector.interestOpsChanged(this);
			}
		}
		
		ByteBuffer poll() {
			if(currentBuf != null && currentBuf.remaining() > 0)
				return currentBuf;
			int current = readPointer.getAndIncrement();
			currentBuf = toWrite.getAndSet(current & (toWrite.length() - 1), null);
			if(currentBuf == null) {
				empty = true;
				selector.interestOpsChanged(this);
			}
			return currentBuf;
		}
		
		public Connection(SocketChannel chan) throws IOException {
			this.chan = chan;
			chan.configureBlocking(false);
			selector.register(this);
			connections.add(this);
		}

		@Override
		public SelectableChannel getChannel() {
			return chan;
		}

		@Override
		public void registrationEvent(NIOConnectionManager manager, SelectionKey key) throws IOException {}

		@Override
		public void selectionEvent(SelectionKey key) throws IOException {
			if(key.isValid() && key.isReadable())
				read();
			if(key.isValid() && key.isWritable())
				write();
		}
		
		ByteBuffer readBuf = ByteBuffer.allocateDirect(4096);
		
		void read() throws IOException {
			try {
				while(true) {
					int read = chan.read(readBuf);
					readBuf.rewind();
					if(read < 0)
						chan.close();
					if(read == 0)
						break;
				}
			} catch(ClosedChannelException ex) {
				chan.close();
			}
		}
		
		ByteBuffer currentBuf;
		
		void write() throws IOException {
			while(true) {
				ByteBuffer buf = poll();
				if(buf == null)
					break;
				if(chan.write(buf) == 0)
					break;
			}
		}

		@Override
		public void doStateChecks(long now) throws IOException {
			if(!chan.isOpen())
				connections.remove(this);
		}

		@Override
		public int calcInterestOps() {
			int ops = SelectionKey.OP_READ;
			if(!empty)
				ops |= SelectionKey.OP_WRITE;
			return ops;
		}
		
	}
	
	void incomingMessage(DHT dht, MessageBase msg) {
		if(msg.getType() != MessageBase.Type.REQ_MSG || msg.getMethod() != MessageBase.Method.GET_PEERS)
			return;
		if(connections.isEmpty())
			return;
		
		GetPeersRequest req = (GetPeersRequest) msg;
		
		StringBuilder b = new StringBuilder();
		Key k = req.getInfoHash();
		String addr = req.getOrigin().getAddress().getHostAddress();
		long now = System.currentTimeMillis();
		
		b.append(now).append('\t').append(k.toString(false)).append('\t').append(addr).append('\n');
		
		ByteBuffer buf = str2buf(b.toString());
		
		connections.forEach(c -> {
			c.add(buf.duplicate());
		});
	}
	
	
	@Override
	public void stop() {
		connections.forEach(c -> {
			try {
				c.chan.close();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		});

	}

}
