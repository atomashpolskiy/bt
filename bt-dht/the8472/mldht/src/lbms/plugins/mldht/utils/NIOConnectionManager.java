/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.utils;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

import lbms.plugins.mldht.kad.DHT;
import lbms.plugins.mldht.kad.DHT.LogLevel;

public class NIOConnectionManager {
	
	ConcurrentLinkedQueue<Selectable> registrations = new ConcurrentLinkedQueue<>();
	ConcurrentLinkedQueue<Selectable> updateInterestOps = new ConcurrentLinkedQueue<>();
	List<Selectable> connections = new ArrayList<>();
	AtomicReference<Thread> workerThread = new AtomicReference<>();
	
	String name;
	Selector selector;
	volatile boolean wakeupCalled;
	
	public NIOConnectionManager(String name) {
		this.name = name;
		try
		{
			selector = Selector.open();
		} catch (IOException e)
		{
			e.printStackTrace();
		}
	}
	
	int iterations;
	
	void selectLoop() {
		
		iterations = 0;
		lastNonZeroIteration = 0;
		
		while(true)
		{
			try
			{
				wakeupCalled = false;
				selector.select(100);
				wakeupCalled = false;
				
				connectionChecks();
				processSelected();
				handleRegistrations();
				updateInterestOps();

					
			} catch (Exception e)
			{
				DHT.log(e, LogLevel.Error);
			}
			
			iterations++;
			
			if(suspendOnIdle())
				break;
		}
	}
	
	void processSelected() throws IOException {
		Set<SelectionKey> keys = selector.selectedKeys();
		for(SelectionKey selKey : keys)
		{
			Selectable connection = (Selectable) selKey.attachment();
			connection.selectionEvent(selKey);
		}
		keys.clear();
	}
	
	
	long lastConnectionCheck;

	/*
	 * checks if connections need to be removed from the selector
	 */
	void connectionChecks() throws IOException {
		if((iterations & 0x0F) != 0)
			return;

		long now = System.currentTimeMillis();
		
		if(now - lastConnectionCheck < 500)
			return;
		lastConnectionCheck = now;
		
		for(Selectable conn : new ArrayList<>(connections)) {
			conn.doStateChecks(now);
			SelectableChannel ch = conn.getChannel();
			SelectionKey k;
			if(ch == null || (k = ch.keyFor(selector)) == null || !k.isValid())
				connections.remove(conn);
		}
	}
	
	void handleRegistrations() throws IOException {
		// register new connections
		Selectable toRegister = null;
		while((toRegister = registrations.poll()) != null)
		{
			SelectableChannel ch = toRegister.getChannel();
			SelectionKey key;
			try {
				key = ch.register(selector, toRegister.calcInterestOps(),toRegister);
			} catch (ClosedChannelException ex) {
				// async close
				continue;
			}
			
			connections.add(toRegister);
			toRegister.registrationEvent(NIOConnectionManager.this,key);
		}
	}
	
	HashSet<Selectable> toUpdate = new HashSet<>();
	
	void updateInterestOps() {
		while(true) {
			Selectable t = updateInterestOps.poll();
			if(t == null)
				break;
			toUpdate.add(t);
		}
		
		toUpdate.forEach(sel -> {
			SelectionKey k = sel.getChannel().keyFor(selector);
			if(k != null && k.isValid())
				k.interestOps(sel.calcInterestOps());
		});
		toUpdate.clear();
	}
	
	int lastNonZeroIteration;
	
	boolean suspendOnIdle() {
		if(connections.size() == 0 && registrations.peek() == null)
		{
			if(iterations - lastNonZeroIteration > 10)
			{
				workerThread.set(null);
				ensureRunning();
				return true;
			}
			return false;
		}
		
		lastNonZeroIteration = iterations;
			
		return false;
	}
	
	private void ensureRunning() {
		while(true)
		{
			Thread current = workerThread.get();
			if(current == null && registrations.peek() != null)
			{
				current = new Thread(this::selectLoop);
				current.setName(name);
				current.setDaemon(true);
				if(workerThread.compareAndSet(null, current))
				{
					current.start();
					break;
				}
			} else
			{
				break;
			}
		}
	}
	
	/**
	 * 
	 * @deprecated method was not threadsafe. users should close their channel instead which will remove it from the selector
	 */
	@Deprecated
	public void deRegister(Selectable connection)
	{
		//connections.remove(connection);
	}
	
	public void register(Selectable connection)
	{
		registrations.add(connection);
		ensureRunning();
		selector.wakeup();
	}
	
	public void interestOpsChanged(Selectable sel)
	{
		updateInterestOps.add(sel);
		if(Thread.currentThread() != workerThread.get() && !wakeupCalled)
		{
			wakeupCalled = true;
			selector.wakeup();
		}
	}
	
	public Selector getSelector() {
		return selector;
	}

}
