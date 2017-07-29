/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.mldht;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lbms.plugins.mldht.kad.DHT;
import lbms.plugins.mldht.kad.KBucketEntry;
import lbms.plugins.mldht.kad.Key;
import lbms.plugins.mldht.kad.PeerAddressDBItem;
import lbms.plugins.mldht.kad.RPCServer;
import lbms.plugins.mldht.kad.DHT.LogLevel;
import lbms.plugins.mldht.kad.tasks.PeerLookupTask;
import lbms.plugins.mldht.kad.utils.AddressUtils;
import lbms.plugins.mldht.kad.utils.ResponseTimeoutFilter;
import lbms.plugins.mldht.utils.NIOConnectionManager;
import the8472.bt.MetadataPool;
import the8472.bt.MetadataPool.Completion;
import the8472.bt.PullMetaDataConnection;
import the8472.bt.PullMetaDataConnection.CONNECTION_STATE;
import the8472.bt.PullMetaDataConnection.CloseReason;
import the8472.bt.PullMetaDataConnection.MetaConnectionHandler;
import the8472.bt.UselessPeerFilter;
import the8472.utils.concurrent.LoggingScheduledThreadPoolExecutor;
import the8472.utils.io.ConnectionAcceptor;

public class TorrentFetcher {
	
	Collection<DHT> dhts;
	ScheduledThreadPoolExecutor timer;
	NIOConnectionManager conMan = new NIOConnectionManager("torrent fetcher");
	ConnectionAcceptor serverSelector;
	
	AtomicInteger socketsIncludingHalfOpen = new AtomicInteger();
	AtomicInteger incomingConnections = new AtomicInteger();
	AtomicInteger openConnections = new AtomicInteger();
	Map<RPCServer, Set<Key>> activeLookups = new HashMap<>();
	ResponseTimeoutFilter tf = new ResponseTimeoutFilter();
	
	List<FetchTask> tasks = new ArrayList<>();
	
	int maxOpen = 10;
	int maxSockets = 1000;
	int maxIncoming = 0;
			
	
	public TorrentFetcher(Collection<DHT> dhts) {
		this.dhts = dhts;
		timer = new LoggingScheduledThreadPoolExecutor(1, LoggingScheduledThreadPoolExecutor.namedDaemonFactory("TorrentFetcher Timer"), t -> DHT.log(t, LogLevel.Fatal));
		timer.setKeepAliveTime(4, TimeUnit.SECONDS);
		timer.allowCoreThreadTimeOut(true);
	}
	
	public void setMaxSockets(int maxHalfOpen) {
		this.maxSockets = maxHalfOpen;
	}
	
	public String adaptiveConnectTimeoutHistogram() {
		return tf.getCurrentStats().toString();
	}
	
	boolean incomingConnection(SocketChannel chan) {
		PullMetaDataConnection con = new PullMetaDataConnection(chan);
		
		if(incomingConnections.get() > maxIncoming)
			return false;
		
		incomingConnections.incrementAndGet();
		
		con.setListener(new MetaConnectionHandler() {
			
			@Override
			public void onTerminate() {
				incomingConnections.decrementAndGet();
				
			}
			
			@Override
			public void onStateChange(CONNECTION_STATE oldState, CONNECTION_STATE newState) {
				if(newState == CONNECTION_STATE.STATE_IH_RECEIVED) {
					Key ih = con.getInfohash();
					try {
						Optional<FetchTask> ft;
						
						synchronized (TorrentFetcher.this) {
							ft = tasks.stream()
									.filter((t -> t.hash.equals(ih)))
									.findAny();
						}
						
						if (!ft.isPresent()) {
							con.terminate("currently not servicing infohash " + ih.toString(false), CloseReason.OTHER);
							return;
						}

						ft.get().registerIncomingConnection(con);
					} catch (IOException e) {
						DHT.log(e, LogLevel.Error);
					}
				}
				
			}
			
			@Override
			public void onConnect() {
				// TODO Auto-generated method stub
				
			}
		});
		conMan.register(con);
		
		return true;
	}
	
	public void setMaxOpen(int maxOpen) {
		this.maxOpen = maxOpen;
	}
	
	public int openConnections() {
		return openConnections.get();
	}
	
	public void maxIncoming(int max) {
		maxIncoming = max;
		serverSelector = new ConnectionAcceptor(this::incomingConnection);
		conMan.register(serverSelector);
	}
	
	public int socketcount() {
		return socketsIncludingHalfOpen.get();
	}
	
	boolean socketLimitsReached() {
		return openConnections.get() > maxOpen || socketsIncludingHalfOpen.get() > maxSockets;
	}
	
	UselessPeerFilter pf;
	
	public void setPeerFilter(UselessPeerFilter pf) {
		this.pf = pf;
	}
	
	ScheduledFuture<?> f = null;
	
	void ensureRunning() {
		synchronized (this) {
			if(f == null && tasks.size() > 0) {
				f = timer.scheduleWithFixedDelay(this::schedule, 0, 1, TimeUnit.SECONDS);
			}
				
		}
	}
	
	void schedule() {
		synchronized (this) {
			if(tasks.size() == 0 && f != null) {
				f.cancel(false);
				f = null;
				return;
			}
			
			startDHTTasks();
			startConnections();
			
		}
	}
	
	
	void startDHTTasks() {
		// choose servers, then pick the task which maximizes the target key distance to all currently running tasks on that server
		// this should avoid running adjacent keys at the same time
		// conversely that means adjacent tasks are scheduled only after the previous one finished, which will make the caches more effective

		while(true) {
			FetchTask best = null;
			List<RPCServer> servers = dhts.stream().filter(DHT::isRunning).map(d -> d.getServerManager().getRandomActiveServer(false)).filter(Objects::nonNull).collect(Collectors.toList());

			if(servers.isEmpty())
				break;

			if(!servers.stream().allMatch(s -> s.getDHT().getTaskManager().queuedCount(s) == 0))
				break;

			
			synchronized (this) {
				Key bestDistance = Key.MIN_KEY;
				
				if(ThreadLocalRandom.current().nextFloat() < 0.05) {
					best = tasks.stream().filter(t -> !t.dhtStarted).findFirst().orElse(null);
				} else {
					for(FetchTask t : tasks) {
						if(t.dhtStarted)
							continue;
						
						Key dist = servers.stream().flatMap(s -> activeLookups.getOrDefault(s, Collections.emptySet()).stream()).map(k -> t.hash.distance(k)).min(Comparator.naturalOrder()).orElse(Key.MAX_KEY);
						
						if(bestDistance.compareTo(dist) <= 0) {
							best = t;
							bestDistance = dist;
						}
						
					}
					
				}

			}
			
			if(best == null)
				break;
			best.lookups(servers.stream());
			// since we only schedule new tasks when the queues are empty we want the manager to start them immediately instead of waiting for timers
			servers.stream().forEach(s -> s.getDHT().getTaskManager().dequeue(s));
		}
	}

	void startConnections() {
		int offset = ThreadLocalRandom.current().nextInt(tasks.size());
		
		for(int i= 0;i<tasks.size();i++) {
			int idx = Math.floorMod(i+offset, tasks.size());
			
			if(socketLimitsReached())
				break;
			
			tasks.get(idx).connections();
		}
		
	}
	
	
	public enum FetchState {
		PENDING,
		SUCCESS,
		FAILURE;
	}
	
	public class FetchTask {
		
		Key hash;
		Instant startTime;
		CompletableFuture<FetchTask> future = new CompletableFuture<>();
		Set<InetSocketAddress> pinged = Collections.newSetFromMap(new ConcurrentHashMap<>()) ;
		Map<InetSocketAddress, PullMetaDataConnection.CONNECTION_STATE> closed = new ConcurrentHashMap<>();
		ConcurrentHashMap<InetSocketAddress, Set<InetAddress>> candidates = new ConcurrentHashMap<>();
		AtomicBoolean running = new AtomicBoolean(true);
		MetadataPool result;
		AtomicInteger thingsBlockingCompletion = new AtomicInteger(1);
		
		Map<InetAddress, PullMetaDataConnection> connections = new ConcurrentHashMap<>();
		Map<Integer, MetadataPool> pools = new ConcurrentHashMap<>();
		
		boolean dhtStarted;
		
		FetchState state = FetchState.PENDING;
		
		public CompletionStage<FetchTask> awaitCompletion() {
			return future;
		}
		
		public Key infohash() {
			return hash;
		}
		
		@Override
		public String toString() {
			String[] str = {
					hash.toString(false),
					"age:",
					Duration.between(startTime, Instant.now()).toString(),
					"cand:",
					String.valueOf(candidates.size()),
					"con active:",
					connections.values().stream().collect(Collectors.groupingBy(PullMetaDataConnection::getState, Collectors.counting())).toString(),
					"con closed:",
					closeCounts().toString()
			};
			
			return String.join(" ", str);
		}

		public FetchState getState() {
			return state;
		}
		
		public Optional<ByteBuffer> getResult() {
			return Optional.ofNullable(result).map(MetadataPool::merge);
		}
		
		public void stop() {
			if(!running.compareAndSet(true, false))
				return;
			if(state == FetchState.PENDING)
				state = FetchState.FAILURE;
			connections.values().forEach(c -> {
				try {
					c.terminate("fetch task finished");
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			});
			remove(this);
			future.complete(this);
		}
		
		public Map<CONNECTION_STATE, Long> closeCounts() {
			return closed.entrySet().stream().collect(Collectors.groupingBy(Map.Entry::getValue, Collectors.counting()));
		}
		
		public int attemptedCount() {
			return connections.size() + closed.size();
		}

		void start() {
			startTime = Instant.now();
		}
		
		void addCandidate(KBucketEntry source, PeerAddressDBItem toAdd) {
			addCandidate(source.getAddress().getAddress() ,toAdd.toSocketAddress());
		}
		
		void addCandidate(InetAddress source, InetSocketAddress toAdd) {
			
			if(pf != null && pf.isBad(toAdd))
				return;
			
			candidates.compute(toAdd, (k, sources) -> {
				Set<InetAddress> newSources = new HashSet<>();
				if(source != null)
					newSources.add(source);
				if(sources != null)
					newSources.addAll(sources);
				return newSources;
			});
		}
		
		MetadataPool getPool(int length) {
			return pools.computeIfAbsent(length, l -> {
				return new MetadataPool(l);
			});
		}
		
		Consumer<PeerLookupTask> conf;
		
		public void configureLookup(Consumer<PeerLookupTask> conf) {
			this.conf = conf;
		}
		
		void lookups(Stream<RPCServer> servers) {
			dhtStarted = true;
			
			servers.forEach(srv -> {
				DHT d = srv.getDHT();
				PeerLookupTask task = new PeerLookupTask(srv, d.getNode(), hash);
				
				
				synchronized (TorrentFetcher.this) {
					activeLookups.computeIfAbsent(srv, unused -> new HashSet<>()).add(task.getTargetKey());
				}

				task.setNoAnnounce(true);
				if(conf != null)
					conf.accept(task);
				task.setResultHandler(this::addCandidate);
				task.addListener(t -> {
					synchronized (TorrentFetcher.this) {
						activeLookups.get(srv).remove(task.getTargetKey());
					}
					
					thingsBlockingCompletion.decrementAndGet();
					checkCompletion();
					timer.execute(TorrentFetcher.this::startDHTTasks);
				});

				thingsBlockingCompletion.incrementAndGet();



				future.thenAccept(x -> task.kill());
				
				d.getTaskManager().addTask(task);
			});

			thingsBlockingCompletion.decrementAndGet();
		}
		
		void checkCompletion() {
			if(thingsBlockingCompletion.get() == 0 && candidates.isEmpty()) {
				stop();
				return;
			}
		}
		
		void registerIncomingConnection(PullMetaDataConnection con) throws IOException {
			if(closed.entrySet().stream().anyMatch(e -> e.getKey().getAddress().equals(con.remoteAddress().getAddress()) && !e.getValue().neverConnected())) {
				con.terminate("already connected", CloseReason.OTHER);
				return;
			}
			
			PullMetaDataConnection existing = connections.putIfAbsent(con.remoteAddress().getAddress(),con);
			
			if(existing != null) {
				if(existing.isState(CONNECTION_STATE.STATE_CONNECTING) && !existing.isIncoming()) {
					existing.terminate("incoming connection takes precedence", CloseReason.OTHER);
					connections.put(con.remoteAddress().getAddress(), con);
				} else {
					con.terminate("connection to remote address already established", CloseReason.OTHER);
					return;
				}
					
			}
			
			decorate(con);
			openConnections.incrementAndGet();
			thingsBlockingCompletion.incrementAndGet();
			
			con.setListener(new MetaConnectionHandler() {
				
				@Override
				public void onTerminate() {
					incomingConnections.decrementAndGet();
				}
				
				@Override
				public void onStateChange(CONNECTION_STATE oldState, CONNECTION_STATE newState) {
					if(newState == CONNECTION_STATE.STATE_CLOSED) {
						processPool(con.getMetaData());
						openConnections.decrementAndGet();
						thingsBlockingCompletion.decrementAndGet();
						connections.remove(con.remoteAddress().getAddress(), con);
						closed.put(con.remoteAddress(), oldState);
					}

				}
				
				@Override
				public void onConnect() {
					// TODO Auto-generated method stub
					
				}
			});
			
		}
		
		void processPool(MetadataPool pool) {
			if (pool == null)
				return;
			if (pool.status() == Completion.SUCCESS) {
				result = pool;
				state = FetchState.SUCCESS;
				stop();
			}
			if (pool.status() == Completion.FAILED) {
				pools.remove(pool.bytes(), pool);
			}
		}
		
		void decorate(PullMetaDataConnection con) {
			con.poolGenerator = this::getPool;
			con.dhtPort = dhts.stream().mapToInt(d -> d.getConfig().getListeningPort()).findAny().getAsInt();
			con.pexConsumer = (toAdd) -> {
				toAdd.forEach(item -> {
					this.addCandidate(con.remoteAddress().getAddress(), item);
				});
			};
			
		}
		
		void connections() {
			checkCompletion();
			
			if(!running.get()) {
				return;
			}
			
			// workaround for JDK-8163353
			if(!closed.isEmpty())
				candidates.keySet().removeAll(closed.keySet());
			
			Comparator<Map.Entry<InetSocketAddress, Set<InetAddress>>> comp = Map.Entry.comparingByValue(Comparator.comparingInt(Set::size));
			comp = comp.reversed();
			// deprioritize teredo addresses
			comp = comp.thenComparing(Map.Entry.comparingByKey(Comparator.comparingInt((InetSocketAddress addr) -> AddressUtils.isTeredo(addr.getAddress()) ? 1 : 0))) ;
			
			InetSocketAddress[] cands = candidates.entrySet().stream().sorted(comp).map(Map.Entry::getKey).toArray(InetSocketAddress[]::new);
			
			int i = 0;
			
			for(InetSocketAddress addr : cands) {
				
				if(connections.containsKey(addr.getAddress()))
					continue;
				
				if(socketLimitsReached())
					break;
				if(i++ > 5)
					break;
				
				
				PullMetaDataConnection con;
						
				try {
					con = new PullMetaDataConnection(hash.getHash(), addr);
				} catch (IOException e) {
					DHT.log(e, LogLevel.Error);
					break;
				}


				if(connections.putIfAbsent(addr.getAddress(), con) != null) {
					try {
						con.terminate("connection to that socket address already open", CloseReason.OTHER);
					} catch (IOException e) {
						DHT.log(e, LogLevel.Error);
					}
					i--;
					continue;
				}

				candidates.remove(addr);


				if(serverSelector != null && serverSelector.getPort() > 0)
					con.ourListeningPort = serverSelector.getPort();
				
				con.keepPexOnlyOpen(closed.values().stream().filter(CONNECTION_STATE.STATE_PEX_ONLY::equals).count() < 20);
				con.setConnectTimeout(tf.getStallTimeout());
				
				decorate(con);
			
				con.setListener(new MetaConnectionHandler() {

					@Override
					public void onTerminate() {
						connections.remove(con.remoteAddress().getAddress(), con);
						
						MetadataPool pool = con.getMetaData();
						processPool(pool);

						if(con.chunksReceived() > 0) {
							synchronized (tf) {
								tf.updateAndRecalc(con.timeToConnect());
							}
						}
							
						thingsBlockingCompletion.decrementAndGet();
						if(pf != null)
							pf.insert(con);
					}
					
					public void onStateChange(CONNECTION_STATE oldState, CONNECTION_STATE newState) {
						if(newState == CONNECTION_STATE.STATE_CLOSED) {
							closed.put(addr, oldState);
							socketsIncludingHalfOpen.decrementAndGet();
						}
							
						if(oldState == CONNECTION_STATE.STATE_CONNECTING && newState != CONNECTION_STATE.STATE_CLOSED) {
							openConnections.incrementAndGet();
						}
							
						if(oldState != CONNECTION_STATE.STATE_INITIAL && oldState != CONNECTION_STATE.STATE_CONNECTING && newState == CONNECTION_STATE.STATE_CLOSED)
							openConnections.decrementAndGet();
					};

					@Override
					public void onConnect() {
						
					}
				});
				thingsBlockingCompletion.incrementAndGet();
				socketsIncludingHalfOpen.incrementAndGet();
				conMan.register(con);
			}
		}
		
	}
	
	void remove(FetchTask t) {
		synchronized (this) {
			tasks.remove(t);
		}
	}
	
	void add(FetchTask t) {
		synchronized (this) {
			tasks.add(t);
		}
		ensureRunning();
	}
	
	public FetchTask fetch(Key infohash) {
		return fetch(infohash, null);
	}
	
	public FetchTask fetch(Key infohash, Consumer<FetchTask> configure) {
		FetchTask t = new FetchTask();
		t.hash = infohash;
		if(configure != null)
			configure.accept(t);
		add(t);
		t.start();
		
		return t;
	}
	
	
	
	
	

}
