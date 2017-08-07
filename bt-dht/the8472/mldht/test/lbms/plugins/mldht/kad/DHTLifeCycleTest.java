/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import lbms.plugins.mldht.DHTConfiguration;
import lbms.plugins.mldht.kad.DHT;
import lbms.plugins.mldht.kad.DHTLogger;
import lbms.plugins.mldht.kad.DHT.DHTtype;
import lbms.plugins.mldht.kad.DHT.LogLevel;
import lbms.plugins.mldht.kad.utils.AddressUtils;
import lbms.plugins.mldht.kad.DHTStatus;

import java.lang.Thread.UncaughtExceptionHandler;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import org.junit.Test;

public class DHTLifeCycleTest {
	
	/**
	 * Test that startup doesn't throw exceptions
	 * 
	 * things not covered:
	 * - routing table loading
	 * - delayed tasks
	 * - bootstrap name resolution
	 * - bootstrap ping attempts
	 */
	@Test
	public void testStartup() throws Exception {
		int port = ThreadLocalRandom.current().nextInt(1024, 65535);
		
		CompletableFuture<Boolean> exceptionCanary = new CompletableFuture<>();
		
		ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
			@Override
			public Thread newThread(Runnable r) {
				Thread t = new Thread(r);
				t.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
					
					@Override
					public void uncaughtException(Thread t, Throwable e) {
						exceptionCanary.completeExceptionally(e);
						
					}
				});
				return t;
			}
		});
		
		DHT dhtInstance = new DHT(DHTtype.IPV4_DHT);
		
		
		
		// TODO: refactor to per-instance logger
		DHT.setLogger(new DHTLogger() {
			
			@Override
			public void log(Throwable t, LogLevel l) {
				// allow bootstrap node resolution to fail in test environment
				if(t instanceof UnknownHostException)
					return;
				exceptionCanary.completeExceptionally(t);
				
			}
			
			@Override
			public void log(String message, LogLevel l) {
								
			}
		});
		
		dhtInstance.setScheduler(scheduler);
		
		Path storagePath = Paths.get(".", "does", "not", "exist");
		
		dhtInstance.start(new DHTConfiguration() {
			
			@Override
			public boolean noRouterBootstrap() {
				return true;
			}
			
			@Override
			public boolean isPersistingID() {
				return false;
			}
			
			@Override
			public Path getStoragePath() {
				return storagePath;
			}
			
			@Override
			public int getListeningPort() {
				return port;
			}
			
			@Override
			public boolean allowMultiHoming() {
				return false;
			}
		});
		
		assertEquals(DHTStatus.Initializing, dhtInstance.getStatus());
		
		// bootstrap is called synchronously during startup and should fall straight through, thus reset the state
		assertEquals(DHT.BootstrapState.NONE, dhtInstance.bootstrapping.get());
		
		assertEquals(1, dhtInstance.getServerManager().getServerCount());
		
		RPCServer srv = dhtInstance.getServerManager().getRandomServer();
		
		scheduler.submit(() -> {
			try {
				assertEquals(RPCServer.State.RUNNING, srv.getState());
				// fake a packet to trigger liveness update
				DatagramChannel chan = DatagramChannel.open();
				chan.connect(new InetSocketAddress(srv.getBindAddress(), srv.getPort()));
				ByteBuffer packet = ByteBuffer.allocate(50);
				packet.put(0, (byte) 'd');
				assertEquals(50, chan.write(packet));
				
				srv.sel.readEvent();
				
				CompletableFuture<RPCServer> cf = dhtInstance.getServerManager().awaitActiveServer().toCompletableFuture();
				
				dhtInstance.getServerManager().refresh(System.currentTimeMillis());
				
				assertEquals(srv, cf.get(500, TimeUnit.MILLISECONDS));
				
			} catch(Exception e) {
				exceptionCanary.completeExceptionally(e);
			}
		}).get();
		
		CompletableFuture<Boolean> wasEmpty = new CompletableFuture<>();
		
		// single-threaded executor -> we can let startup tasks complete and then stop the DHT from the pool itself
		// thus there should be no pending tasks on the executor
		scheduler.submit(() -> {
			dhtInstance.stop();

			scheduler.purge();
			
			wasEmpty.complete(scheduler.getQueue().isEmpty());
		}).get();
		
		assertTrue("no tasks should remain queued after stop()", wasEmpty.get());
		
		assertEquals(DHTStatus.Stopped, dhtInstance.getStatus());
		
		assertEquals("no messages should have been sent on a bootstrapless startup", 0, dhtInstance.getStats().getNumSentPackets());
		
		scheduler.shutdown();
		
		assertTrue("all tasks terminated", scheduler.awaitTermination(10, TimeUnit.MILLISECONDS));
		
		exceptionCanary.complete(true);
		
		// check for async exceptions
		exceptionCanary.get();
		
		assertFalse("should not create storage path, that's the caller's duty", Files.isDirectory(storagePath));
		
		
	}
	
	@Test
	public void testBindFilter() {
		DHT dht = NodeFactory.buildDHT(DHTtype.IPV4_DHT);
		dht.getNode().initKey(null);
		dht.setScheduler(Executors.newSingleThreadScheduledExecutor());
		AtomicReference<Predicate<InetAddress>> predicate = new AtomicReference<>(null);
		
		dht.config = new DHTConfiguration() {
			
			@Override
			public boolean noRouterBootstrap() {
				return true;
			}
			
			@Override
			public boolean isPersistingID() {
				// TODO Auto-generated method stub
				return false;
			}
			
			@Override
			public Path getStoragePath() {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public int getListeningPort() {
				// TODO Auto-generated method stub
				return 0;
			}
			
			@Override
			public boolean allowMultiHoming() {
				return false;
			}
			
			@Override
			public Predicate<InetAddress> filterBindAddress() {
				return predicate.get();
			}
		};
		
		predicate.set((unused) -> true);
		RPCServerManager srvman = dht.getServerManager();
		srvman.refresh(System.currentTimeMillis());
		assertNotEquals(0, srvman.getServerCount());
		
		predicate.set((unused) -> false);
		srvman.doBindChecks();
		assertEquals(0, srvman.getServerCount());
		
		// wildcard addr same as allowing all addresses of that family
		predicate.set((addr) -> addr instanceof Inet4Address && addr.isAnyLocalAddress());
		srvman.refresh(System.currentTimeMillis());
		assertNotEquals(0, srvman.getServerCount());
		
		// select a specific address
		InetAddress localAddr = AddressUtils.nonlocalAddresses().filter(Inet4Address.class::isInstance).findAny().get();
		predicate.set((addr) -> addr.equals(localAddr));
		srvman.doBindChecks();
		srvman.refresh(System.currentTimeMillis());
		assertEquals(localAddr, srvman.getAllServers().get(0).getBindAddress());
		
		
	}

}
