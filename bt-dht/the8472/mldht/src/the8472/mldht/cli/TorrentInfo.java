/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.mldht.cli;

import static the8472.utils.Functional.typedGet;

import the8472.bencode.PrettyPrinter;
import the8472.bencode.Tokenizer.BDecodingException;
import the8472.bt.TorrentUtils;
import the8472.utils.concurrent.SerializedTaskExecutor;

import lbms.plugins.mldht.kad.Key;
import lbms.plugins.mldht.kad.utils.ThreadLocalUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TorrentInfo {
	
	Path source;
	ByteBuffer raw;
	Map<String, Object> root;
	Map<String, Object> info;
	Charset encoding = StandardCharsets.UTF_8;
	boolean truncate = true;
	
	
	public TorrentInfo(Path source) {
		this.source = source;
	}
	
	void readRaw() {
		if(raw != null)
			return;
		try(FileChannel chan = FileChannel.open(source, StandardOpenOption.READ)) {
			raw = chan.map(MapMode.READ_ONLY, 0, chan.size());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
	
	void decode() {
		if(root != null)
			return;
		readRaw();
		root = ThreadLocalUtils.getDecoder().decode(raw.duplicate());
		typedGet(root, "info", Map.class).ifPresent(i -> info = i);
		if(info != null) {
			String charset = typedGet(info, "encoding", byte[].class).map(b -> new String(b, StandardCharsets.ISO_8859_1)).orElse(null);
			if(charset != null) {
				try {
					this.encoding = Charset.forName(charset);
				} catch (Exception e) {
					System.err.println("Charset " + charset + "not supported, falling back to " + encoding.name());
				}
			}
		}
	}
	
	Key infoHash() {
		return TorrentUtils.infohash(raw);
	}
	
	Optional<String> name() {
		decode();
		Optional<String> name = typedGet(info, "name.utf-8", byte[].class).map(b -> new String(b, StandardCharsets.UTF_8));
		if(!name.isPresent()) {
			name = typedGet(info, "name", byte[].class).map(b -> new String(b, encoding));
		}
		
		return name;
	}
	
	List<Map<String, Object>> files() {
		return typedGet(info, "files", List.class).map((List l) -> {
			return (List<Map<String, Object>>)l.stream().filter(Map.class::isInstance).collect(Collectors.toList());
		}).orElse(Collections.emptyList());
	}
	
	String raw() {
		decode();
		PrettyPrinter p = new PrettyPrinter();
		p.indent("  ");
		p.guessHumanReadableStringValues(true);
		p.truncateHex(truncate);
		p.append(root);
		return p.toString();
	}
	
	
	public static void main(String[] argsAry) throws IOException, InterruptedException {
		List<String> args = new ArrayList<>(Arrays.asList(argsAry));
		
		boolean printRaw = ParseArgs.extractBool(args, "-raw");
		boolean noTrunc = ParseArgs.extractBool(args, "-notrunc");
		boolean recursive = ParseArgs.extractBool(args, "-r");
		boolean printLargest = ParseArgs.extractBool(args, "-largest");
		
		
		Stream<Path> torrents = args.parallelStream().unordered().map(Paths::get).filter(Files::exists).flatMap(p -> {
			try {
				return Files.find(p, recursive ? Integer.MAX_VALUE :  1 , (f, attr) -> {
					return attr.isRegularFile() && attr.size() > 0;
				},  FileVisitOption.FOLLOW_LINKS);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}); //.collect(Collectors.toSet()).parallelStream().unordered();

		
		Consumer<String> printer = SerializedTaskExecutor.runSerialized((String s) -> {
			System.out.println(s);
		});
		
		String newline = "\\u000a|\\u000b|\\u000c|\\u000d|\\u0085|\\u2028|\\u2029";
		
		torrents.map(p -> {
			TorrentInfo ti = new TorrentInfo(p);
			try {
				ti.decode();
			} catch(BDecodingException ex) {
				return p.toString() + " does not appear to be a bencoded file: " + ex.getMessage();
			}

			if(printRaw) {
				ti.truncate = !noTrunc;
				return p.toString() + "\n" + ti.raw() + '\n';
			}
				
			
			if(ti.info == null)
				return p.toString() + " does not contain an info dictionary";
			
			long length = typedGet(ti.info, "length", Long.class).orElse(0L);
			long largestSize = length;
			int numFiles = 1;
			
			StringBuilder result = new StringBuilder();
			Optional<String> name = ti.name();
			
			if(!name.isPresent()) {
				return p.toString() + " does not contain a name field";
			}
			
			String largestFile = "";
			
			List<Map<String, Object>> files = ti.files();
			
			if(!files.isEmpty()) {
				length = files.stream().mapToLong(e -> typedGet(e, "length", Long.class).orElse(0L)).sum();
				numFiles = files.size();
				Map<String, Object> largest = files.stream().max(Comparator.comparing(e -> typedGet(e, "length", Long.class).orElse(0L))).get();
				largestSize = typedGet(largest, "length", Long.class).orElse(0L);
				
				List<?> path = typedGet(largest, "path.utf-8", List.class).orElse(null);
				if(path == null)
					path = typedGet(largest, "path", List.class).orElse(null);
				
				largestFile = path.stream().filter(byte[].class::isInstance).map(b -> new String((byte[]) b, StandardCharsets.UTF_8)).collect(Collectors.joining("/"));
				largestFile = largestFile.replaceAll(newline, " ");
			}
			
			
			result.append(p.toString());
			result.append(" ");
			ti.name().map(s -> s.replaceAll(newline, " ")).ifPresent(result::append);
			
			if(printLargest) {
				if(numFiles > 1) {
					result.append('/');
					result.append(largestFile);
				}

				result.append(" size:");
				result.append(largestSize);
				result.append('/');
				result.append(length);
				result.append(" files:");
				result.append(numFiles);
			} else {
				result.append(" size:");
				result.append(length);
				result.append(" files:");
				result.append(numFiles);
			}
			
			result.append(" ih:");
			result.append(ti.infoHash().toString(false));
			
			return result.toString();
		}).forEach(printer::accept);
		

	}

}
