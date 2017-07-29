/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.mldht.indexing;

import static the8472.bencode.Utils.buf2str;
import static the8472.bencode.Utils.hex2ary;
import static the8472.bencode.Utils.str2buf;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import lbms.plugins.mldht.kad.DHT;
import lbms.plugins.mldht.kad.Key;
import lbms.plugins.mldht.kad.ScrapeResponseHandler;
import lbms.plugins.mldht.kad.tasks.PeerLookupTask;
import lbms.plugins.mldht.utils.NIOConnectionManager;
import lbms.plugins.mldht.utils.Selectable;
import the8472.mldht.Component;
import the8472.utils.ConfigReader;

public class ActiveLookupProvider implements Component {
	
	Collection<DHT> dhts;
	NIOConnectionManager manager;
	
	@Override
	public void start(Collection<DHT> dhts, ConfigReader config) {
		this.dhts = dhts;
		manager = new NIOConnectionManager("active-lookups");
		try {
			manager.register(new Server());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	
	@Override
	public void stop() {
		// TODO Auto-generated method stub
		
	}
	
	void lookupRequested(Connection c, byte[] infoHash) {
		List<PeerLookupTask> tasks = new ArrayList<PeerLookupTask>();
		
		Key k = new Key(infoHash);
		
		AtomicInteger counter = new AtomicInteger();
		
		ScrapeResponseHandler scrape = new ScrapeResponseHandler();
		
		for(DHT d : dhts) {
			if(!d.isRunning())
				continue;
			PeerLookupTask t = d.createPeerLookup(infoHash);
			if(!d.getTaskManager().canStartTask(t))
				return;
			counter.incrementAndGet();
			t.setFastTerminate(false);
			t.setLowPriority(true);
			t.setScrapeHandler(scrape);
			t.addListener(x -> {
				if(counter.decrementAndGet() <= 0) {
					lookupDone(c, k, scrape);
				};
			});
			tasks.add(t);
		}
		
		c.send(str2buf("starting\t"+k.toString(false)+'\n'));
		
		tasks.forEach(t -> t.getRPC().getDHT().getTaskManager().addTask(t));
	}
	
	void lookupDone(Connection c, Key k, ScrapeResponseHandler h) {
		h.process();
		c.send(str2buf("done\t"+k.toString(false)+"\tscrapeSeeds:"+h.getScrapedSeeds()+"\tscrapePeers:"+h.getScrapedPeers()+"\tdirect:"+h.getDirectResultCount()+'\n'));
	}
	
	class Connection implements Selectable {
		
		SocketChannel chan;
		
		volatile boolean writePending = false;
		
		Queue<ByteBuffer> toWrite = new ConcurrentLinkedQueue<>();
		
		public void send(ByteBuffer b) {
			//if(toWrite.isEmpty()) {
				writePending = true;
				manager.interestOpsChanged(this);
			//}
				
			
			toWrite.add(b);
		}
		
		public Connection(SocketChannel chan) throws IOException {
			this.chan = chan;
			chan.configureBlocking(false);
			manager.register(this);
		}

		@Override
		public SelectableChannel getChannel() {
			return chan;
		}

		@Override
		public void registrationEvent(NIOConnectionManager manager, SelectionKey key) throws IOException {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void selectionEvent(SelectionKey key) throws IOException {
			if(key.isValid() && key.isWritable())
				write();
			if(key.isValid() && key.isReadable())
				read();
		}
		
		ByteBuffer readBuffer = ByteBuffer.allocate(1024);
		
		void read() throws IOException {
			while(true) {
				int bytes = chan.read(readBuffer);
				if(bytes < 0) {
					chan.close();
					return;
				}
				if(bytes == 0)
					break;
				
				readBuffer.flip();
				processBuffer(readBuffer);
				readBuffer.compact();

			}
		}
		
		void processBuffer(ByteBuffer buf) {
			// scan for newline
			ByteBuffer line = buf.slice();
			int i = 0;
			buf.mark();
			
			while(buf.remaining() > 0) {
				i++;
				if(buf.get() == '\n') {
					buf.mark();
					line.limit(i-1);
					line(line);
					i = 0;
					line = buf.slice();
				}
			}
			
			buf.reset();

			
		}
		
		void line(ByteBuffer buf) {
			if(buf.remaining() != 40)
				return;
			String hex = buf2str(buf);
			lookupRequested(this, hex2ary(hex));
			//Key key = new Key(hex2ary(hex));
		}
		
		ByteBuffer writeBuffer;
		
		
		void write() throws IOException {
			try {
				while(true) {
					if(writeBuffer == null || writeBuffer.remaining() == 0)
						writeBuffer = toWrite.poll();
					if(writeBuffer == null) {
						writePending = false;
						manager.interestOpsChanged(this);
						return;
					}
					
					if(chan.write(writeBuffer) == 0)
						break;
					
					
				}
			} catch (IOException e) {
				chan.close();;
			}
			
			
		}

		@Override
		public void doStateChecks(long now) throws IOException {
			// TODO Auto-generated method stub
			
		}

		@Override
		public int calcInterestOps() {
			int ops = SelectionKey.OP_READ;
			if(writePending)
				ops |= SelectionKey.OP_WRITE;
			
			return ops;
		}
		
	}
	
	class Server implements Selectable {
		
		ServerSocketChannel chan;
		
		public Server() throws IOException {
			chan = ServerSocketChannel.open();
			chan.configureBlocking(false);
			chan.bind(new InetSocketAddress(InetAddress.getByAddress(new byte[16]), 36578));
		}

		@Override
		public SelectableChannel getChannel() {
			return chan;
		}

		@Override
		public void registrationEvent(NIOConnectionManager manager, SelectionKey key) throws IOException {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void selectionEvent(SelectionKey key) throws IOException {
			if(key.isValid() && key.isAcceptable()) {
				SocketChannel c;
				while((c = chan.accept()) != null) {
					new Connection(c);
				}
			}
				
		}

		@Override
		public void doStateChecks(long now) throws IOException {
			// TODO Auto-generated method stub
			
		}

		@Override
		public int calcInterestOps() {
			return SelectionKey.OP_ACCEPT;
		}
		
		
	}
	

}
