/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.mldht.indexing;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static the8472.utils.Functional.autoclose;
import static the8472.utils.Functional.shortCircuitingflatMap;
import static the8472.utils.Functional.typedGet;
import static the8472.utils.Functional.unchecked;

import the8472.bencode.BDecoder;
import the8472.bencode.BEncoder;
import the8472.bt.TorrentUtils;
import the8472.bt.UselessPeerFilter;
import the8472.mldht.Component;
import the8472.mldht.TorrentFetcher;
import the8472.mldht.TorrentFetcher.FetchTask;
import the8472.mldht.indexing.TorrentDumper.FetchStats.State;
import the8472.utils.ConfigReader;
import the8472.utils.ShufflingBag;
import the8472.utils.concurrent.LoggingScheduledThreadPoolExecutor;
import the8472.utils.concurrent.SerializedTaskExecutor;
import the8472.utils.io.FileIO;

import lbms.plugins.mldht.kad.DHT;
import lbms.plugins.mldht.kad.KBucketEntry;
import lbms.plugins.mldht.kad.Key;
import lbms.plugins.mldht.kad.RPCServer;
import lbms.plugins.mldht.kad.TaskBuilder;
import lbms.plugins.mldht.kad.DHT.LogLevel;
import lbms.plugins.mldht.kad.messages.AnnounceRequest;
import lbms.plugins.mldht.kad.messages.GetPeersRequest;
import lbms.plugins.mldht.kad.messages.MessageBase;
import lbms.plugins.mldht.kad.utils.ThreadLocalUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class TorrentDumper implements Component {
	
	Collection<DHT> dhts;
	Path storageDir = Paths.get(".", "dump-storage");
	Path statsDir = storageDir.resolve("stats");
	Path torrentDir = storageDir.resolve("torrents");
	
	private static final int MAX_STAT_FILE_SIZE = 8*1024;
	private static final int QUOTA = 100_000;
	
	ScheduledThreadPoolExecutor scheduler;
	
	ConcurrentSkipListMap<Key, FetchStats> fromMessages;
	AtomicInteger quota = new AtomicInteger(QUOTA);

	ConcurrentMap<InetAddress, Long> blocklist = new ConcurrentHashMap<>();
	
	TorrentFetcher fetcher;
	UselessPeerFilter pf;
	
	static class FetchStats {
		final Key k;
		int insertCount = 1;
		List<KBucketEntry> recentSources;
		long creationTime = -1;
		long lastFetchTime = -1;
		int fetchCount = 0;
		State state = State.INITIAL;
		
		static final int max_entries = 10;
		
		enum State {
			INITIAL,
			PRIORITY,
			FAILED;
			
			public Path stateDir(Path statsdir) {
				return statsdir.resolve(name().toLowerCase());
			}
			
			
		}

		public FetchStats(Key k, Consumer<FetchStats> init) {
			Objects.requireNonNull(k);
			this.k = k;
			if(init != null)
				init.accept(this);
		}

		static FetchStats fromBencoded(Map<String, Object> map) {
			Key k = typedGet(map, "k", byte[].class).map(Key::new).orElseThrow(() -> new IllegalArgumentException("missing key in serialized form"));
			
			return new FetchStats(k, fs -> {
				fs.recentSources = typedGet(map, "sources", List.class).map((List l) -> {
					List<Map<String, Object>> typedList = l;
					return typedList.stream().map(KBucketEntry::fromBencoded).collect(Collectors.toCollection(ArrayList::new));
				}).orElse(new ArrayList<>());
				
				typedGet(map, "state", byte[].class).map(b -> new String(b, StandardCharsets.ISO_8859_1)).map(str -> {
					try {
						return State.valueOf(str);
					} catch (IllegalArgumentException e) {
						return null;
					}
				}).ifPresent(st -> fs.state = st);
				
				typedGet(map, "created", Long.class).ifPresent(time -> fs.creationTime = time);
				typedGet(map, "cnt", Long.class).ifPresent(cnt -> fs.insertCount = cnt.intValue());
				typedGet(map, "fetchtime", Long.class).ifPresent(time -> fs.lastFetchTime = time);
				typedGet(map, "fetchcount", Long.class).ifPresent(i -> fs.fetchCount = i.intValue());
				
			});
		}
		
		Map<String, Object> forBencoding() {
			Map<String, Object> map = new TreeMap<>();
			
			map.put("k", k.getHash());
			map.put("cnt", insertCount);
			map.put("sources", recentSources.stream().map(s -> s.toBencoded()).collect(Collectors.toCollection(ArrayList::new)));
			map.put("created", creationTime);
			map.put("state", state.name());
			map.put("fetchtime", lastFetchTime);
			map.put("fetchcount", fetchCount);
			
			return map;
		}

		public Key getK() {
			return k;
		}
		
		public FetchStats merge(FetchStats other) {
			if(!k.equals(other.k))
				throw new IllegalArgumentException("key mismatch");
			
			insertCount += other.insertCount;
			fetchCount += other.fetchCount;
			recentSources.addAll(other.recentSources);
			if(recentSources.size() > max_entries) {
				recentSources.sort(KBucketEntry.LAST_SEEN_ORDER);
				recentSources.subList(0, recentSources.size() - max_entries).clear();
			}
			
			creationTime = min(creationTime, other.creationTime);
			lastFetchTime = max(lastFetchTime, other.lastFetchTime);
			
			return this;
		}
		
		public void setState(State newState) {
			state = newState;
		}
		
		public Path name(Path dir, String suffix) {
			String hex = k.toString(false);
			return dir.resolve(hex.substring(0, 2)).resolve(hex.substring(2, 4)).resolve(hex+suffix);
		}
		
		public Path statsName(Path statsDir, State st) {
			if(st == null)
				st = state;
			return name(st.stateDir(statsDir), ".stats");
			
		}
		
		
	}

	@Override
	public void start(Collection<DHT> dhts, ConfigReader config) {
		this.dhts = dhts;
		fromMessages = new ConcurrentSkipListMap<>();
		// purge + dump + prefetch + short-running tasks -> 4
		scheduler = new LoggingScheduledThreadPoolExecutor(4, new LoggingScheduledThreadPoolExecutor.NamedDaemonThreadFactory("torrent dumper"), this::log);
		
		fetcher = new TorrentFetcher(dhts);
		
		fetcher.setMaxOpen(40);
		fetcher.maxIncoming(50);

		dhts.forEach(d -> d.addIncomingMessageListener(this::incomingMessage));
		pf = new UselessPeerFilter();
		try {
			Files.createDirectories(torrentDir);
			for(State st : FetchStats.State.values()) {
				Files.createDirectories(st.stateDir(statsDir));
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		
		// XXX: fetcher.setPeerFilter(pf); // filter seems overly aggressive. investigate if we still need it or can improve it
		
		scheduler.scheduleWithFixedDelay(singleThreadedDumpStats, 10, 10, TimeUnit.SECONDS);
		scheduler.scheduleWithFixedDelay(singleThreadedPrefetch, 30, 2, TimeUnit.SECONDS);
		scheduler.scheduleWithFixedDelay(singleThreadedFetches, 10, 1, TimeUnit.SECONDS);
		scheduler.scheduleWithFixedDelay(this::cleanBlocklist, 1, 1, TimeUnit.MINUTES);
		scheduler.scheduleWithFixedDelay(this::diagnostics, 30, 30, TimeUnit.SECONDS);
		scheduler.scheduleWithFixedDelay(this::scrubActive, 10, 20, TimeUnit.SECONDS);
		scheduler.scheduleWithFixedDelay(pf::clean, 10, 10, TimeUnit.MINUTES);
		scheduler.schedule(this::sampling, 2, TimeUnit.MINUTES);
		
		scheduler.scheduleWithFixedDelay(() -> {
			// long-running things working on the filesystem go here to avoid blocking all threads in the pool
			try {
				this.purgeStats();
			} catch (Exception e) {
				log(e);
			}
			
		}, 5, 15, TimeUnit.MINUTES);
	}
	
	void log(Throwable t) {
		DHT.log(t, LogLevel.Error);
	}
	
	void sampling() {
		TaskBuilder.fromInstances(dhts).sampleInfoHashes(16, "Torrent Dumper Sampling", (k, addr, srcid) -> {
			process(k, srcid, addr, null);
		}).whenComplete((v, ex) -> {
			if(ex != null) {
				log(ex);
			}
			scheduler.schedule(this::sampling, 7, TimeUnit.HOURS);
		});
		
	}
	
	void cleanBlocklist() {
		long now = System.currentTimeMillis();
		blocklist.entrySet().removeIf(e -> {
			return (now - e.getValue()) > TimeUnit.MINUTES.toMillis(10);
		});
		
	}
	
	void incomingMessage(DHT d, MessageBase m) {
		if(d.getMismatchDetector().isIdInconsistencyExpected(m.getOrigin(), m.getID()))
			return;
		
		if(m instanceof GetPeersRequest) {
			GetPeersRequest gpr = (GetPeersRequest) m;
			
			RPCServer srv = m.getServer();
			
			Key theirID = gpr.getID();
			
			if(d.getNode().isLocalId(theirID))
				return;
			
			Key ourId = srv.getDerivedID();
			Key target = gpr.getInfoHash();

			if(Stream.of(theirID, ourId, target).distinct().count() != 3)
				return;

			int myCloseness = ourId.distance(target).leadingOneBit();
			int theirCloseness = theirID.distance(target).leadingOneBit();
			
			
			if(theirCloseness > myCloseness && theirCloseness - myCloseness >= 8)
				return; // they're looking for something that's significantly closer to their own ID than we are
			process(gpr.getInfoHash(), theirID, gpr.getOrigin(), null);
		}
		if(m instanceof AnnounceRequest) {
			AnnounceRequest anr = (AnnounceRequest) m;
			process(anr.getInfoHash(), anr.getID(), anr.getOrigin(), anr.getNameUTF8().orElse(null));
		}
	}
	
	void process(Key targetId, Key sourceNodeId, InetSocketAddress src, String name) {
		if(quota.get() < 1)
			return;
		
		FetchStats f = new FetchStats(targetId, init -> {
			init.recentSources = new ArrayList<>();
			init.recentSources.add(new KBucketEntry(src, sourceNodeId));
			init.insertCount = 1;
			init.creationTime = System.currentTimeMillis();
		});
		
		// if there are bursts, only take the first one
		if(fromMessages.putIfAbsent(targetId, f) == null) {
			int newquota = quota.decrementAndGet();
			if(newquota == QUOTA >> 1)
				scheduler.execute(singleThreadedDumpStats);
		}
			
	}
	
	final Runnable singleThreadedDumpStats = SerializedTaskExecutor.onceMore(this::dumpStats);
	
	void dumpStats() {
		long now = System.currentTimeMillis();
		
		ByteBuffer buf = ByteBuffer.allocateDirect(MAX_STAT_FILE_SIZE);
		
		List<Entry<Key, FetchStats>> workSet = new ArrayList<>();
		
		for(Iterator<Entry<Key, FetchStats>> it = fromMessages.entrySet().iterator(); it.hasNext();){
			Entry<Key, FetchStats> e = it.next();
			
			FetchStats toStore = e.getValue();
			if(Files.exists(toStore.name(torrentDir, ".torrent"))) {
				it.remove();
				continue;
			}
			
			workSet.add(e);
		}
		
		workSet.forEach((entry) -> {
			
			Key k = entry.getKey();
			FetchStats toStore = entry.getValue();
			
			if(!fromMessages.remove(k, toStore))
				return;
			
			try {
				
				Optional<Path> existing = Stream.of(toStore.statsName(statsDir, FetchStats.State.INITIAL), toStore.statsName(statsDir, FetchStats.State.FAILED), toStore.statsName(statsDir, FetchStats.State.PRIORITY)).filter(Files::isRegularFile).findFirst();

				if(!existing.isPresent()) {
					// only throttle IPs for new hashes we don't already know about and wouldn't try anyway
					if(activeCount.get() > 50 && blocklist.putIfAbsent(toStore.recentSources.get(0).getAddress().getAddress(), now) != null)
						return;
				}
				
				if(existing.isPresent()) {
					Path p = existing.get();
					try(FileChannel ch = FileChannel.open(p, StandardOpenOption.READ)) {
						buf.clear();
						while(ch.read(buf) != -1);
						buf.flip();
						FetchStats old = FetchStats.fromBencoded(new BDecoder().decode(buf));
						
						
						Collection<InetAddress> oldAddrs = old.recentSources.stream().map(e -> e.getAddress().getAddress()).collect(Collectors.toList());
						Collection<InetAddress> newAddrs = toStore.recentSources.stream().map(e -> e.getAddress().getAddress()).collect(Collectors.toList());
						
						// avoid double-taps promoting things to the priority list
						if(oldAddrs.containsAll(newAddrs) && old.state == FetchStats.State.INITIAL)
							return;
						
						toStore.merge(old);
						
						if(old.state != FetchStats.State.INITIAL)
							toStore.state = old.state;
						
					} catch (IOException e) {
						log(e);
					}
				}
				
				if(toStore.state == State.INITIAL && toStore.insertCount > 1) {
					toStore.state = State.PRIORITY;
					if(existing.isPresent())
						Files.deleteIfExists(existing.get());
				}
					
				
				Path statsFile = toStore.statsName(statsDir, null);
				
				Path tempFile = Files.createTempFile(statsDir, statsFile.getFileName().toString(), ".stats");
				
				try(FileChannel ch = FileChannel.open(tempFile, StandardOpenOption.WRITE)) {
					buf.clear();
					new BEncoder().encodeInto(toStore.forBencoding(), buf);
					while(buf.hasRemaining())
						ch.write(buf);
					ch.close();
					Files.createDirectories(statsFile.getParent());
					Files.move(tempFile, statsFile, StandardCopyOption.ATOMIC_MOVE);
				} catch(Exception ex) {
					Files.deleteIfExists(tempFile);
					throw ex;
				}

				
			} catch (Exception e) {
				log(e);
			}
			
			
			
		});
		
		quota.set(QUOTA);
	}
	
	void purgeStats() {
		Path failedDir = FetchStats.State.FAILED.stateDir(statsDir);
		Path initialDir = FetchStats.State.INITIAL.stateDir(statsDir);
		
		long now = System.currentTimeMillis();
		
		try {
			Supplier<Stream<Path>> supplyInitial = () -> unchecked(() -> fetchStatsStream(Stream.of(initialDir)));
			Supplier<Stream<Path>> supplyFailed = () -> unchecked(() -> fetchStatsStream(Stream.of(failedDir)));
			Predicate<FetchStats> deleteInitial = stat -> now - stat.creationTime > TimeUnit.DAYS.toMillis(4);
			Predicate<FetchStats> deleteFailed = stat -> {
				long timeSinceFetch = now - stat.lastFetchTime;
				
				// this fetch attempt was from State.INITIAL, don't keep it around for so long, it's unlikely it'll get another hit
				if(stat.insertCount == 1)
					return timeSinceFetch > TimeUnit.HOURS.toMillis(1);
				
				long timeToFetch = stat.lastFetchTime - stat.creationTime;
				timeToFetch = Math.max(TimeUnit.HOURS.toMillis(1), timeToFetch);
				
				// the longer it takes us to chew through the data the longer we keep things around
				return timeSinceFetch > timeToFetch * 4 ;
			};
			
			BiConsumer<Supplier<Stream<Path>>, Predicate<FetchStats>> doDeletes = (a, b) -> {
				unchecked(() -> {
					// probe first 100, only do a full pass if > N% are eligible for deletion
					boolean doFullIteration = autoclose(a, (p) -> {
						long cnt = filesToFetchers(p)
							.filter(Objects::nonNull)
							.limit(100)
							.filter(b)
							.count();
						return cnt > 33;
					});
					
					if(doFullIteration) {
						autoclose(a, p -> {
							filesToFetchers(p).filter(Objects::nonNull)
								.filter(b)
								.map(stat -> stat.statsName(statsDir, null))
								.forEach(path -> unchecked(() -> Files.deleteIfExists(path)));
							return null;
						});
					}
					
					return null;
				});
				
			};
			
			doDeletes.accept(supplyInitial, deleteInitial);
			doDeletes.accept(supplyFailed, deleteFailed);
			

		} catch (Exception e) {
			log(e);
		}

		/*
		// 0 -> stats, 1 -> {failed|initial|prio}, 2 -> 00, 3 -> 00/00
		try (Stream<Path> st = Files.find(statsDir, 3, (p, attr) -> attr.isDirectory())) {
			st.filter(d -> {
				try (DirectoryStream<Path> dst = Files.newDirectoryStream(d)) {
					return !dst.iterator().hasNext();
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}).forEach(d -> {
				try {
					Files.deleteIfExists(d);
				} catch(DirectoryNotEmptyException e) {
					// someone on another thread wrote to it. do nothing
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
		} catch (UncheckedIOException | IOException e) {
			log(e);
		}*/

			
		
		
	}
	
	
	Stream<Path> dirShuffler(Path p) {
		if(!Files.isDirectory(p))
			return null;
		List<Path> sub;
		try(Stream<Path> st = Files.list(p)) {
			sub = st.collect(Collectors.toList());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		if(sub.isEmpty())
			try {
				Files.delete(p);
			} catch (IOException e) {
				// ignore, it's an opportunistic delete
			}
		Collections.shuffle(sub);
		return sub.stream();
	}
	
	
	Stream<Path> fetchStatsStream(Stream<Path> rootDirs) throws IOException {
		
		
		


		// this does not use a true shuffle, the stream will emit some clusters at the 8bit keyspace granularity
		// it's closer to linear scan from a random starting point
		// but polling in small batches should lead to reasonable task randomization without expensive full directory traversal
		Stream<Path> leafs = shortCircuitingflatMap(rootDirs, (d -> {
			Stream<Path> sublevel = Stream.of(d);
			sublevel = shortCircuitingflatMap(sublevel, this::dirShuffler);
			sublevel = shortCircuitingflatMap(sublevel, this::dirShuffler);
			sublevel = shortCircuitingflatMap(sublevel, this::dirShuffler);
			return sublevel;
		}));

		
		return leafs;
	}
	
	Stream<FetchStats> filesToFetchers(Stream<Path> st) {
		Set<Key> skip = skipSet();
		
		ThreadLocal<ByteBuffer> bufProvider = new ThreadLocal<>();
		bufProvider.set(ByteBuffer.allocateDirect(MAX_STAT_FILE_SIZE));
		
		return st.filter(p -> {
			Key k = new Key(p.getFileName().toString().substring(0, 40));
			return !skip.contains(k);
		}).map(p -> {
			try(FileChannel ch = FileChannel.open(p, StandardOpenOption.READ)) {
				ByteBuffer buf = bufProvider.get();
				buf.clear();
				while(-1 != ch.read(buf))
				buf.flip();
				return FetchStats.fromBencoded(ThreadLocalUtils.getDecoder().decode(buf));
			} catch(NoSuchFileException ex) {
				// expect async deletes
				return null;
			} catch(IOException ex) {
				log(ex);
				return null;
			}
		}).filter(Objects::nonNull);
		
	}
	
	// avoids that adjacent tasks are started at the same time. interleaving them with other tasks allows for better cache-priming
	Queue<FetchStats> toFetchNext = new ShufflingBag<>();
	
	Runnable singleThreadedPrefetch = SerializedTaskExecutor.onceMore(this::prefetch);
	
	
	Set<Key> skipSet() {
		Set<Key> dedup = new HashSet<>();
		
		dedup.addAll(activeTasks.keySet());
		synchronized (toFetchNext) {
			toFetchNext.stream().map(FetchStats::getK).forEach(dedup::add);
		}
		
		return dedup;
	}
	
	void prefetch() {
		synchronized (toFetchNext) {
			if(toFetchNext.size() >=  maxFetches() / 2)
				return;
		}

		
		Set<Key> dedup = skipSet();
		
		
		try {
			Path prio = FetchStats.State.PRIORITY.stateDir(statsDir);
			Path normal = FetchStats.State.INITIAL.stateDir(statsDir);
			

			// strides of 8 * maxtasks/4. should be >= low watermark
			int strides = maxFetches() / 4;
			int[] added = new int[1];
			for(int i = 0;i< strides ;i++) {
				Stream<FetchStats> pst = filesToFetchers(fetchStatsStream(Stream.of(prio))).limit(200);
				Stream<FetchStats> nst = filesToFetchers(fetchStatsStream(Stream.of(normal))).limit(200);
				
				try(Stream<FetchStats> st = Stream.concat(pst, nst)) {
					st.filter(stats -> !dedup.contains(stats.k)).limit(8).forEach(e -> {
						dedup.add(e.getK());
						synchronized (toFetchNext) {
							toFetchNext.add(e);
						}
						added[0] += 1;
					});
					
				};
			}
			int remaining = strides * 8 - added[0];
			
			// if we have not found enough stats on the filesystem steal directly from the unprocessed incoming messages
			for(Iterator<Entry<Key, FetchStats>> it = fromMessages.subMap(Key.createRandomKey(), true, Key.MAX_KEY, true).entrySet().iterator();it.hasNext(); ) {
				if(remaining <= 0)
					break;
				Map.Entry<Key, FetchStats> e = it.next();
				if(dedup.contains(e.getKey()))
					continue;
				if(!fromMessages.remove(e.getKey(), e.getValue()))
					continue;
				
				dedup.add(e.getKey());
				synchronized (toFetchNext) {
					toFetchNext.add(e.getValue());
				}
				remaining--;
			}
		} catch (Exception e) {
			log(e);
		}
	}
	
	
	Runnable singleThreadedFetches = SerializedTaskExecutor.onceMore(this::startFetches);
	
	void startFetches() {
		scheduler.execute(singleThreadedPrefetch);

		int max = maxFetches();
		
		while(activeCount.get() < max) {
			FetchStats st;
			synchronized (toFetchNext) {
				st = toFetchNext.poll();
			}
			if(st == null)
				return;
			fetch(st);
		}
	}
	
	AtomicInteger activeCount = new AtomicInteger();
	ConcurrentHashMap<Key, FetchTask> activeTasks = new ConcurrentHashMap<>();
	
	int maxFetches() {
		// we need enough tasks to keep many RPC servers busy
		int minServers = dhts.stream().mapToInt(d -> d.getServerManager().getActiveServerCount()).min().orElse(0);
		return minServers  * 2 + 100;
	}
	
	void scrubActive() {
		
		// as long as there are young connections it means some fraction of the fetch tasks dies quickly
		// we're fine with other ones taking longer as long as that's the case
		long youngConnections = activeTasks.values().stream().filter(t -> t.attemptedCount() < 5).count();
		
		if(youngConnections > 15 || activeCount.get() < maxFetches() * 0.9)
			return;
		
		
		Comparator<Map.Entry<FetchTask, Integer>> comp = Map.Entry.comparingByValue();
		comp = comp.reversed();
		
		activeTasks.values().stream().map(t -> new AbstractMap.SimpleEntry<>(t, t.attemptedCount())).filter(e -> e.getValue() > 70).sorted(comp).limit(10).forEachOrdered(e -> {
			e.getKey().stop();
		});
	}
	
	void fetch(FetchStats stats) {
		Key k = stats.getK();
		
		if(activeTasks.containsKey(k))
			return;

		
		FetchTask t = fetcher.fetch(k, (fetch) -> {
			fetch.configureLookup(lookup -> {
				// XXX: lookup.setFastTerminate(true); // fast mode seems to be too aggressive, disable until we can investigate. relaxed taskmanager limits still lead to decent performance anyway
				lookup.filterKnownUnreachableNodes(true);
				lookup.setLowPriority(true);
			});
		});
		
		activeCount.incrementAndGet();
		activeTasks.put(k, t);
		
		t.awaitCompletion().thenRunAsync(() -> {
				taskFinished(stats, t);
		}, scheduler);
	}
	
	void taskFinished(FetchStats stats, FetchTask t) {
		activeCount.decrementAndGet();
		stats.recentSources.stream().max(Comparator.comparingLong(KBucketEntry::getLastSeen)).ifPresent(kbe -> {
			blocklist.remove(kbe.getAddress().getAddress());
		});
		activeTasks.remove(t.infohash());
		try {
			for(FetchStats.State st : FetchStats.State.values()) {
				Files.deleteIfExists(stats.statsName(statsDir, st));
			}
			
			Optional<ByteBuffer> result = t.getResult();
			
			if(!result.isPresent()) {
				stats.setState(FetchStats.State.FAILED);
				stats.fetchCount++;
				stats.lastFetchTime = System.currentTimeMillis();
				
				Path failedStatsFile = stats.statsName(statsDir, null);
				Files.createDirectories(failedStatsFile.getParent());
				
				try(FileChannel statsChan = FileChannel.open(failedStatsFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
					statsChan.write(new BEncoder().encode(stats.forBencoding(), 4*1024));
				}
				return;
			}
			ByteBuffer infoDict = result.get();
			
			Path torrentFile = stats.name(torrentDir, ".torrent");
			Files.createDirectories(torrentFile.getParent());
			
			try(FileChannel chan = FileChannel.open(torrentFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
				ByteBuffer torrent = TorrentUtils.wrapBareInfoDictionary(infoDict);
				while(torrent.hasRemaining())
					chan.write(torrent);
			}
		} catch (Exception e) {
			log(e);
		}
		scheduler.execute(singleThreadedFetches);
	}
	
	void diagnostics() {
		try {
			FileIO.writeAndAtomicMove(storageDir.resolve("dumper.log"), (p) -> {
				p.format("Fetcher:%n established: %d%n sockets: %d%n%n adaptive timeout:%n%s %n%n", fetcher.openConnections(), fetcher.socketcount(), fetcher.adaptiveConnectTimeoutHistogram());
				
				p.format("FetchTasks: %d %n", activeCount.get());
				activeTasks.values().forEach(ft -> {
					p.println(ft.toString());
				});
			});
		} catch (IOException e) {
			log(e);
		}
	}
	

	@Override
	public void stop() {
		scheduler.shutdown();
		activeTasks.values().forEach(FetchTask::stop);
	}

}
