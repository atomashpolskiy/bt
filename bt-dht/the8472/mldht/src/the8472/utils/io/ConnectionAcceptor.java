/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.utils.io;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.function.Predicate;

import lbms.plugins.mldht.kad.DHT;
import lbms.plugins.mldht.kad.DHT.LogLevel;
import lbms.plugins.mldht.kad.utils.AddressUtils;
import lbms.plugins.mldht.utils.NIOConnectionManager;
import lbms.plugins.mldht.utils.Selectable;

public class ConnectionAcceptor implements Selectable {
	
	NIOConnectionManager conHandler;
	ServerSocketChannel channel;
	InetAddress addr;
	int port = 0;
	
	final Predicate<SocketChannel> acceptor;
	
	
	public ConnectionAcceptor(Predicate<SocketChannel> acc) {
		acceptor = acc;
		
		try
		{
			channel = ServerSocketChannel.open();
			channel.configureBlocking(false);
		} catch (IOException e)
		{
			DHT.log(e, LogLevel.Error);
		}
	}
	
	public void setAddressType(Class<? extends InetAddress> addressType) {
		addr = AddressUtils.getDefaultRoute(addressType);
	}
	
	public void setPort(int port) {
		this.port = port;
	}
	
	public int getPort() {
		return port;
	}
	
	
	public SelectableChannel getChannel() {
		return channel;
	}
	
	public void registrationEvent(NIOConnectionManager manager, SelectionKey key) throws IOException {
		conHandler = manager;
		channel.socket().bind(new InetSocketAddress(addr, port), 100);
		port = channel.socket().getLocalPort();
		conHandler.interestOpsChanged(this);
	}
	
	public void selectionEvent(SelectionKey key) throws IOException {
		if(key.isAcceptable())
		{
			while(true)
			{
				SocketChannel chan = channel.accept();
				if(chan == null)
					break;
				
				if(!acceptor.test(chan))
				{
					chan.close();
					continue;
				}
			}

		}
	}
	
	public void doStateChecks(long now) throws IOException {
		// TODO Auto-generated method stub
	}


	@Override
	public int calcInterestOps() {
		return SelectionKey.OP_ACCEPT;
	}
	
	
}
