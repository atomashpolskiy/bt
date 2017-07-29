/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.mldht;

import static the8472.utils.Functional.unchecked;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.Formatter;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lbms.plugins.mldht.kad.DHT;
import lbms.plugins.mldht.kad.Database;
import lbms.plugins.mldht.kad.Database.PeersSeeds;
import lbms.plugins.mldht.kad.GenericStorage;
import lbms.plugins.mldht.kad.GenericStorage.StorageItem;
import lbms.plugins.mldht.kad.KBucketEntry;
import lbms.plugins.mldht.kad.Key;
import lbms.plugins.mldht.kad.Node;
import lbms.plugins.mldht.kad.Node.RoutingTable;
import lbms.plugins.mldht.kad.Prefix;
import the8472.bencode.Utils;
import the8472.utils.Arrays;
import the8472.utils.io.FileIO;
import the8472.utils.io.NetMask;

public class Diagnostics {
	
	Collection<DHT> dhts;
	Path logDir;
	
	public void init(Collection<DHT> dhts, Path logDir) {
		
		this.dhts = dhts;
		this.logDir = logDir;

		dhts.stream().findAny().ifPresent(d -> d.getScheduler().scheduleWithFixedDelay(this::writeAll, 10, 30, TimeUnit.SECONDS));
	}
	
	void writeAll() {
		try {
			printMain();
			printRoutingTable();
			printDatabases();
			printPUTStorage();
			

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	void printPUTStorage() throws IOException {
		Path file = logDir.resolve("putDB.log");
		
		FileIO.writeAndAtomicMove(file, writer -> dhts.stream().filter(DHT::isRunning).forEach(d -> {
			writer.append("Type: " + d.getType().shortName + "\n");
			formatStorage(writer, d.getStorage());
		}));
	}
	
	public void formatStorage(Appendable writer, GenericStorage storage) {
		Formatter f = new Formatter(writer);
		
		storage.getItems().forEach((k, v) -> {
			f.format("%s mutable:%b seq:%d %n",
				k,
				v.mutable(),
				v.seq()
			);
			f.format("%s%n%n", Utils.stripToAscii(v.getRawValue()));
		});
	}

	void printDatabases() throws Exception {
		Path file = logDir.resolve("getPeersDB.log");
		
		FileIO.writeAndAtomicMove(file, writer -> dhts.stream().filter(DHT::isRunning).forEach(d -> {
			writer.append("Type: " + d.getType().shortName + "\n");
			formatDatabase(writer, d.getDatabase());
		}));
	}
	
	/* TODO: fix this

java.lang.IllegalArgumentException: Comparison method violates its general contract!
	at java.util.TimSort.mergeLo(TimSort.java:777)
	at java.util.TimSort.mergeAt(TimSort.java:514)
	at java.util.TimSort.mergeCollapse(TimSort.java:441)
	at java.util.TimSort.sort(TimSort.java:245)
	at java.util.Arrays.sort(Arrays.java:1512)
	at java.util.stream.SortedOps$SizedRefSortingSink.end(SortedOps.java:348)
	at java.util.stream.AbstractPipeline.copyInto(AbstractPipeline.java:482)
	at java.util.stream.AbstractPipeline.wrapAndCopyInto(AbstractPipeline.java:471)
	at java.util.stream.ForEachOps$ForEachOp.evaluateSequential(ForEachOps.java:151)
	at java.util.stream.ForEachOps$ForEachOp$OfRef.evaluateSequential(ForEachOps.java:174)
	at java.util.stream.AbstractPipeline.evaluate(AbstractPipeline.java:234)
	at java.util.stream.ReferencePipeline.forEach(ReferencePipeline.java:418)
	at the8472.mldht.Diagnostics.formatDatabase(Diagnostics.java:102)
	at the8472.mldht.Diagnostics.lambda$14(Diagnostics.java:91)
	at java.util.stream.ForEachOps$ForEachOp$OfRef.accept(ForEachOps.java:184)
	at java.util.stream.ReferencePipeline$2$1.accept(ReferencePipeline.java:175)
	at java.util.ArrayList$ArrayListSpliterator.forEachRemaining(ArrayList.java:1374)
	at java.util.stream.AbstractPipeline.copyInto(AbstractPipeline.java:481)
	at java.util.stream.AbstractPipeline.wrapAndCopyInto(AbstractPipeline.java:471)
	at java.util.stream.ForEachOps$ForEachOp.evaluateSequential(ForEachOps.java:151)
	at java.util.stream.ForEachOps$ForEachOp$OfRef.evaluateSequential(ForEachOps.java:174)
	at java.util.stream.AbstractPipeline.evaluate(AbstractPipeline.java:234)
	at java.util.stream.ReferencePipeline.forEach(ReferencePipeline.java:418)
	at the8472.mldht.Diagnostics.lambda$3(Diagnostics.java:89)
	at the8472.mldht.Diagnostics.writeAndAtomicMove(Diagnostics.java:221)
	at the8472.mldht.Diagnostics.printDatabases(Diagnostics.java:89)
	at the8472.mldht.Diagnostics.writeAll(Diagnostics.java:56)
	at java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:511)
	at java.util.concurrent.FutureTask.runAndReset(FutureTask.java:308)
	at the8472.utils.concurrent.NonblockingScheduledExecutor$SchedF.run(NonblockingScheduledExecutor.java:266)
	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1142)
	at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:617)
	at java.lang.Thread.run(Thread.java:745)

	 
	 */
	
	public void formatDatabase(Appendable writer, Database db) {
		Map<Key, PeersSeeds> items = db.getData();
		
		Formatter f = new Formatter(writer);
		
		f.format("Keys: %d Entries: %d%n", items.size(), items.values().stream().collect(Collectors.summingInt(l -> l.size())));
		
		items.entrySet().stream().sorted((a,b) -> b.getValue().size() - a.getValue().size()).forEach(e -> {
			PeersSeeds v = e.getValue();
			f.format("%s s:%5d p:%5d ∑:%5d%n", e.getKey().toString(false), v.seeds().size(), v.peers().size(), v.size());
		});
		
		f.format("%n======%n%n");
		
		
		Instant now = Instant.now();
		
		
		
		items.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEachOrdered((e) -> {
			Key k = e.getKey();
			PeersSeeds v = e.getValue();
			
			f.format("%s (s:%d p:%d)%n", k.toString(false), v.seeds().size(), v.peers().size());
			
			Stream.concat(v.seeds().stream(), v.peers().stream()).sorted((a,b) -> Arrays.compareUnsigned(a.getData(), b.getData())).forEachOrdered(i -> {
				unchecked(() -> writer.append("  ").append(i.toString()).append(" age: ").append(Duration.between(Instant.ofEpochMilli(i.getCreatedAt()), now).toString()).append('\n'));
			});
			
			
			f.format("%n");
		});
		
		f.format("%n%n");
	}
	
	private void printRoutingTable() throws Exception {
		Path file = logDir.resolve("routingTable.log");
		
		FileIO.writeAndAtomicMove(file, writer -> dhts.stream().filter(DHT::isRunning).forEach(d -> this.formatRoutingTable(writer, d.getNode())));
	}
	
	public void formatRoutingTable(Appendable writer, Node node) {
		Collection<Key> localIds = node.localIDs();
		RoutingTable entries = node.table();
		Collection<NetMask> masks = node.getTrustedNetMasks();
		
		NavigableMap<Key, PeersSeeds> peerDB = new TreeMap<>(node.getDHT().getDatabase().getData());
		NavigableMap<Key, StorageItem> putDB = new TreeMap<>(node.getDHT().getStorage().getItems());
		
		Formatter f = new Formatter(writer);
		
		f.format("Type: %s%n", node.getDHT().getType().shortName);
		
		
		f.format("%nThrottled:%n");
		
		Comparator<Map.Entry<InetAddress, Long>> comp = Map.Entry.<InetAddress, Long>comparingByValue()
				.reversed().thenComparing(Comparator.comparing(e -> e.getKey().getAddress(), Arrays::compareUnsigned));
		
		node.throttledEntries().filter(e -> e.getValue() > Node.throttleThreshold).sorted(comp).forEach(e -> {
			f.format("%5d %s%n", e.getValue().intValue(), e.getKey());
		});
		
		f.format("%n");
		
		
		Consumer<Prefix> dbMapper = (Prefix p) -> {
			NavigableMap<Key, PeersSeeds> subPeers = peerDB.subMap(p.first(), true, p.last(), true);
			if(subPeers.isEmpty())
				f.format("%28s", "");
			else
				f.format("ihash:%5d s:%5d p:%5d ",
						subPeers.size(),
						subPeers.values().stream().mapToInt(e -> e.seeds().size()).sum(),
						subPeers.values().stream().mapToInt(e -> e.peers().size()).sum());
			
			NavigableMap<Key, StorageItem> subStorage = putDB.subMap(p.first(), true, p.last(), true);
			
			if(subStorage.isEmpty())
				f.format("%14s", "");
			else
				f.format("storage:%5d ", subStorage.size());
			
			
			return;
		};
		
		entries.stream().forEach(tableEntry -> {
			Optional<Key> localId = localIds.stream().filter(i -> tableEntry.prefix.isPrefixOf(i)).findAny();
			String isHomeBucket = localId.map(k -> "[Home:"+k.toString(false)+"]").orElse("");
			f.format("%s/%-3s main:%d rep:%d ", new Key(tableEntry.prefix).toString(false), tableEntry.prefix.getDepth(), tableEntry.getBucket().getNumEntries(), tableEntry.getBucket().getNumReplacements());
			dbMapper.accept(tableEntry.prefix);
			f.format("%s %s%n", tableEntry.prefix, isHomeBucket);
		});
		
		f.format("%n======%n%n");
		
		entries.stream().forEach(tableEntry -> {
			Optional<Key> localId = localIds.stream().filter(i -> tableEntry.prefix.isPrefixOf(i)).findAny();
			String isHomeBucket = localId.map(k -> "[Home:"+k.toString(false)+"]").orElse("");
			f.format("%40s/%-3d ", new Key(tableEntry.prefix).toString(false), tableEntry.prefix.getDepth());
			dbMapper.accept(tableEntry.prefix);
			f.format("%s%n", isHomeBucket);
			
			List<KBucketEntry> bucketEntries = tableEntry.getBucket().getEntries();
			if(bucketEntries.size() > 0) {
				f.format("  Entries (%d)%n", bucketEntries.size());
				bucketEntries.forEach(bucketEntry -> f.format("    %s %s%n", bucketEntry,masks.stream().anyMatch(m -> m.contains(bucketEntry.getAddress().getAddress())) ? "[trusted]" : ""));
			}
			List<KBucketEntry> replacements = tableEntry.getBucket().getReplacementEntries();
			if(replacements.size() > 0) {
				f.format("  Replacements (%d)%n", replacements.size());
				replacements.forEach(bucketEntry -> f.format("    %s%n", bucketEntry));
			}
			if(bucketEntries.size() > 0 || replacements.size() > 0)
				f.format("%n");
		});
		
		f.format("%n%n");
		
	}
	
	
	
	

	void printMain() throws Exception {
		Path diagnostics = logDir.resolve("diagnostics.log");
		
		FileIO.writeAndAtomicMove(diagnostics, w -> dhts.stream().filter(DHT::isRunning).forEach(d -> d.printDiagnostics(w)));
	}
	
	
}
