/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.mldht;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TransferQueue;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import lbms.plugins.mldht.DHTConfiguration;
import lbms.plugins.mldht.kad.DHT;
import lbms.plugins.mldht.kad.DHT.LogLevel;
import lbms.plugins.mldht.kad.DHTLogger;
import the8472.utils.ConfigReader;
import the8472.utils.FilesystemNotifications;
import the8472.utils.XMLUtils;
import the8472.utils.concurrent.NonblockingScheduledExecutor;
import the8472.utils.io.NetMask;

public class Launcher {
	
	Supplier<InputStream> configSchema = () -> Launcher.class.getResourceAsStream("config.xsd");
	
	Supplier<InputStream> configDefaults = () -> Launcher.class.getResourceAsStream("config-defaults.xml");
	
	List<Component> components = new ArrayList<>();
	
	private ConfigReader configReader;

	class XmlConfig implements DHTConfiguration {
		
		int port;
		boolean multihoming;
		
		void update() {
			port = configReader.getLong("//core/port").orElse(49001L).intValue();
			multihoming = configReader.getBoolean("//core/multihoming").orElse(true);
		}

		
		@Override
		public boolean noRouterBootstrap() {
			return !configReader.getBoolean("//core/useBootstrapServers").orElse(true);
		}

		@Override
		public boolean isPersistingID() {
			return configReader.getBoolean("//core/persistID").orElse(true);
		}

		@Override
		public Path getStoragePath() {
			return Paths.get(".");
		}

		@Override
		public int getListeningPort() {
			return port;
		}

		@Override
		public boolean allowMultiHoming() {
			return multihoming;
		}
	}
	
	XmlConfig config = new XmlConfig();

	List<DHT> dhts = new ArrayList<>();

	volatile boolean running = true;

	Thread shutdownHook = new Thread(this::onVmShutdown, "shutdownHook");
	
	ScheduledExecutorService scheduler;
	DHTLogger logger;
	
	public Launcher() {
		configReader = new ConfigReader(Paths.get(".", "config.xml"), configDefaults, configSchema);
		configReader.read();
		
		scheduler = new NonblockingScheduledExecutor("mlDHT", Math.max(Runtime.getRuntime().availableProcessors(), 4), (t, ex) ->  {
			logger.log(ex, LogLevel.Fatal);
		});
	}

	private void onVmShutdown() {
		initiateShutdown();
		shutdownCleanup();
	}
	
	FilesystemNotifications notifications = new FilesystemNotifications();


	protected void start() throws Exception {
		config.update();
		configReader.addChangeCallback(config::update);
		
		Arrays.asList(DHT.DHTtype.values()).stream().filter(t -> !this.isIPVersionDisabled(t.PREFERRED_ADDRESS_TYPE)).forEach(type -> {
			dhts.add(new DHT(type));
		});
		
		dhts.forEach(d -> {
			d.addSiblings(dhts);
			d.setScheduler(scheduler);
		});

		
		Path logDir = Paths.get("./logs/");
		Files.createDirectories(logDir);
		

		final Path log = logDir.resolve("dht.log");
		Path exLog = logDir.resolve("exceptions.log");

		//final PrintWriter logWriter = ;
		final PrintWriter exWriter = new PrintWriter(Files.newBufferedWriter(exLog, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE, StandardOpenOption.WRITE), true);
		
		configReader.getAll(XMLUtils.buildXPath("//component/className",null)).forEach(className -> {
			try {
				Class<Component> clazz = (Class<Component>) Class.forName(className);
				components.add(clazz.newInstance());
			} catch (Exception e1) {
				throw new RuntimeException(e1);
			}
		});
		
		logger = new DHTLogger() {

			private String timeFormat(LogLevel level) {
				return "[" + Instant.now().toString() + "][" + level.toString() + "] ";
			}

			TransferQueue<String> toLog = new LinkedTransferQueue<>();

			Thread writer = new Thread() {
				@Override
				public void run() {
					try {
						FileChannel.open(log, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING).close();

						while (true) {

							String toWrite = toLog.take();
							
							// log rotate at 1GB
							if(Files.size(log) > 1024*1024*1024)
								Files.move(log, log.resolveSibling("dht.log.1"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);


							try(PrintWriter logWriter = new PrintWriter(Files.newBufferedWriter(log, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND))) {
								do {
									logWriter.println(toWrite);
								} while((toWrite = toLog.poll()) != null);
								logWriter.flush();
							}

						}

					} catch (InterruptedException | IOException e) {
						e.printStackTrace();
					}
				}
			};

			{
				writer.setDaemon(true);
				writer.setName("LogWriter");
				writer.start();
			}

			public void log(String message, LogLevel l) {
				try {
					toLog.put(timeFormat(l) + message);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			public void log(Throwable e, LogLevel l) {
				exWriter.append(timeFormat(l));
				e.printStackTrace(exWriter);
				exWriter.flush();
			}
		};
 
		DHT.setLogger(logger);
		
		new Diagnostics().init(dhts, logDir);

		setLogLevel();
		configReader.registerFsNotifications(notifications);
		configReader.addChangeCallback(this::setLogLevel);

		for (DHT dht : dhts) {
			if(isIPVersionDisabled(dht.getType().PREFERRED_ADDRESS_TYPE))
				continue;
			dht.start(config);
			dht.bootstrap();
			// dht.addIndexingListener(dumper);
		}
		
		// need to run this after startup, Node doesn't exist before then
		setTrustedMasks();
		configReader.addChangeCallback(this::setTrustedMasks);
		
		components.forEach(c -> c.start(dhts, configReader));

		Runtime.getRuntime().addShutdownHook(shutdownHook);
		
		Path shutdown = Paths.get("./shutdown");
		
		if(!Files.exists(shutdown))
			Files.createFile(shutdown);
		
		notifications.addRegistration(shutdown, (path, kind) -> {
			if(path.equals(shutdown)) {
				initiateShutdown();
			}
		});
		
		// need 1 non-daemon-thread to keep VM alive
		while(running) {
			synchronized (this) {
				this.wait();
			}
		}
		
		shutdownCleanup();

	}
	
	private void setLogLevel() {
		String rawLevel = configReader.get(XMLUtils.buildXPath("//core/logLevel")).orElse("Info");
		LogLevel level = LogLevel.valueOf(rawLevel);
		DHT.setLogLevel(level);
	}
	
	private void setTrustedMasks() {
		Collection<NetMask> masks = configReader.getAll(XMLUtils.buildXPath("//core/clusterNodes/networkPrefix")).map(NetMask::fromString).collect(Collectors.toList());
		dhts.forEach((d) -> {
			if(d.isRunning())
				d.getNode().setTrustedNetMasks(masks);
		});
	}
	
	private boolean isIPVersionDisabled(Class<? extends InetAddress> type) {
		long disabled = configReader.getLong("//core/disableIPVersion").orElse(-1L);
		if(disabled == 6 && type.isAssignableFrom(Inet6Address.class))
			return true;
		if(disabled == 4 && type.isAssignableFrom(Inet4Address.class))
			return true;
		return false;
	}

	public void initiateShutdown() {
		if(running) {
			running = false;
			synchronized (this) {
				this.notifyAll();
			}
		}
	}
	
	boolean cleanupDone = false;
	
	void shutdownCleanup() {
		synchronized (this) {
			if(cleanupDone)
				return;
			cleanupDone = true;
			components.forEach(Component::stop);
			dhts.forEach(DHT::stop);
		}
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		new Launcher().start();
	}

}
