/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.mldht.cli;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

import lbms.plugins.mldht.kad.DHT;
import lbms.plugins.mldht.utils.NIOConnectionManager;
import lbms.plugins.mldht.utils.Selectable;
import the8472.bencode.BDecoder;
import the8472.mldht.Component;
import the8472.utils.ConfigReader;

public class Server implements Component {
	
	public static int SERVER_PORT = 33348;
	
	
	NIOConnectionManager conMan = new NIOConnectionManager("CLI-server");
	
	ServerSocketChannel acceptor;
	
	Collection<DHT> dhts;
	
	public Server() {
		// TODO Auto-generated constructor stub
	}
	
	@Override
	public void start(Collection<DHT> dhts, ConfigReader config) {
		this.dhts = dhts;
		
		try {
			acceptor = ServerSocketChannel.open();
			acceptor.configureBlocking(false);
			acceptor.setOption(StandardSocketOptions.SO_REUSEADDR, true);
			acceptor.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), SERVER_PORT));
			
			
			conMan.register(new Selectable() {
				
				@Override
				public void selectionEvent(SelectionKey key) throws IOException {
					if(key.isAcceptable())
						accept();
				}
				
				@Override
				public void registrationEvent(NIOConnectionManager manager, SelectionKey key) throws IOException {
					
				}
				
				@Override
				public SelectableChannel getChannel() {
					return acceptor;
				}
				
				@Override
				public void doStateChecks(long now) throws IOException {
				}
				
				@Override
				public int calcInterestOps() {
					return SelectionKey.OP_ACCEPT;
				}
			});
			
			
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	
	void accept() {
		try {
			SocketChannel chan = acceptor.accept();
			chan.configureBlocking(false);
			chan.socket().setSoTimeout(0);
			chan.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
			
			conMan.register(new Selectable() {
				
				// 4 bytes message length
				ByteBuffer header = ByteBuffer.allocate(4);
				ByteBuffer payload = null;
				
				ByteBuffer currentReadTarget = header;

				
				@Override
				public void selectionEvent(SelectionKey key) throws IOException {
					if(!chan.isOpen()) {
						conMan.deRegister(this);
						return;
					}
					if(key.isValid() && key.isReadable())
						read();
					if(key.isValid() && key.isWritable())
						write();
				}
				
				void read() throws IOException {
					try {
						int read = chan.read(currentReadTarget);
						
						// end of stream
						if(read == -1) {
							header = null;
							conMan.interestOpsChanged(this);
						}
							
						
						if(currentReadTarget.remaining() == 0) {
							currentReadTarget.flip();
							
							if(currentReadTarget == header) {
								payload = ByteBuffer.allocate(header.getInt(0));
								currentReadTarget = payload;
							} else {
								
								process(payload);
								payload = null;
								
								header.clear();
								currentReadTarget = header;
							}
							
							
						}
					} catch (IOException e) {
						chan.close();
					}
										
				}
				
				Deque<ByteBuffer> writes = new ConcurrentLinkedDeque<>();
				
				
				void process(ByteBuffer buf) {
					BDecoder decoder = new BDecoder();
					Map<String, Object> map = decoder.decode(buf);
					List<byte[]> args = (List<byte[]>) map.get("arguments");
					CommandProcessor processor = CommandProcessor.from(args, (b) -> {
						ByteBuffer h = ByteBuffer.allocate(4);
						h.putInt(0, b.remaining());
						synchronized (writes) {
							writes.add(h);
							writes.add(b);
						}
						conMan.interestOpsChanged(this);
					}, dhts);
					processor.currentWorkDir = Paths.get(new String((byte[])map.get("cwd"), StandardCharsets.UTF_8));
					processor.active = chan::isOpen;
					try {
						processor.process();
					} catch(Exception e) {
						processor.handleException(e);
					}
					
				}
				
				void write() throws IOException {
					ByteBuffer toWrite;
					while((toWrite = writes.pollFirst()) != null) {
						try {
							chan.write(toWrite);
						} catch (IOException e) {
							chan.close();
							return;
						}
						if(toWrite.remaining() > 0) {
							writes.addFirst(toWrite);
							break;
						}
					}
					
					if(writes.isEmpty())
						conMan.interestOpsChanged(this);
				}
				
				@Override
				public void registrationEvent(NIOConnectionManager manager, SelectionKey key) throws IOException {
					// TODO Auto-generated method stub
					
				}
				
				@Override
				public SelectableChannel getChannel() {
					return chan;
				}
				
				@Override
				public void doStateChecks(long now) throws IOException {
					// TODO Auto-generated method stub
					
				}
				
				@Override
				public int calcInterestOps() {
					int ops = 0;
					if(header != null)
						ops = SelectionKey.OP_READ;
					if(writes.peek() != null)
						ops |= SelectionKey.OP_WRITE;
					return ops;
				}
			});
			
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	
	@Override
	public void stop() {
		try {
			acceptor.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	

}
