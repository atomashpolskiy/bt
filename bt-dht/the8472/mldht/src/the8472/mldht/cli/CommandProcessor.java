/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.mldht.cli;

import static the8472.utils.Functional.tap;
import static the8472.utils.Functional.unchecked;

import the8472.bencode.BEncoder;
import the8472.mldht.cli.commands.Burst;
import the8472.mldht.cli.commands.Get;
import the8472.mldht.cli.commands.GetPeers;
import the8472.mldht.cli.commands.GetTorrent;
import the8472.mldht.cli.commands.Help;
import the8472.mldht.cli.commands.Ping;
import the8472.mldht.cli.commands.Put;
import the8472.mldht.cli.commands.Sampling;

import lbms.plugins.mldht.kad.DHT;

import java.io.CharArrayWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public abstract class CommandProcessor {
	
	protected Consumer<ByteBuffer> writer;
	protected Collection<DHT> dhts;
	protected List<String> arguments;
	protected Path currentWorkDir = Paths.get("");
	
	BooleanSupplier active = () -> true;
	
	static Map<String, Class<? extends CommandProcessor>> SUPPORTED_COMMANDS = tap(new HashMap<>(), m -> {
		m.put("BURST", Burst.class);
		m.put("PING", Ping.class);
		m.put("HELP", Help.class);
		m.put("GETTORRENT", GetTorrent.class);
		m.put("GETPEERS", GetPeers.class);
		m.put("GET", Get.class);
		m.put("PUT", Put.class);
		m.put("SAMPLE", Sampling.class);
	});
	
	public static CommandProcessor from(List<byte[]> rawArgs, Consumer<ByteBuffer> writer, Collection<DHT> dhts) {
		List<String> args = rawArgs.stream().map(b -> new String(b, StandardCharsets.UTF_8)).collect(Collectors.toCollection(ArrayList::new));
		
		String commandName = args.size() > 0 ? args.get(0).toUpperCase() : "HELP";
		Class<? extends CommandProcessor> clazz = Optional.<Class<? extends CommandProcessor>>ofNullable(SUPPORTED_COMMANDS.get(commandName)).orElse(Help.class);
		
		CommandProcessor proc = unchecked(() -> clazz.newInstance());
		proc.writer = writer;
		proc.dhts = dhts;
		proc.arguments = args.size() > 1 ? args.subList(1, args.size()) : Collections.emptyList();
		return proc;
	}
	
	protected abstract void process();
	
	protected void handleException(Throwable ex) {
		Writer w = new CharArrayWriter(1024);
		PrintWriter pw = new PrintWriter(w);
		ex.printStackTrace(pw);
		printErr(w.toString());
		exit(200);
	}
	
	protected void println(String str) {
		Map<String, Object> map = new HashMap<>();
		map.put("action", "sysout");
		byte[] bytes = (str + '\n').getBytes(StandardCharsets.UTF_8);
		map.put("payload", bytes);
		writer.accept(new BEncoder().encode(map, bytes.length + 40));
	}

	protected void printErr(String str) {
		Map<String, Object> map = new HashMap<>();
		map.put("action", "syserr");
		map.put("payload", str.getBytes(StandardCharsets.UTF_8));
		writer.accept(new BEncoder().encode(map, str.length()*4 + 40));
	}
	
	protected void exit(int code) {
		active = () -> false;
		Map<String, Object> map = new HashMap<>();
		map.put("action", "exit");
		map.put("exitCode", code);
		writer.accept(new BEncoder().encode(map, 64));
	}
	
	protected boolean isRunning() {
		return active.getAsBoolean();
	}
	

}
