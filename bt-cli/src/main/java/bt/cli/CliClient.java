/*
 * Copyright (c) 2016—2017 Andrei Tomashpolskiy and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package bt.cli;

import bt.Bt;
import bt.BtClientBuilder;
import bt.cli.Options.LogLevel;
import bt.data.Storage;
import bt.data.file.FileSystemStorage;
import bt.dht.DHTConfig;
import bt.dht.DHTModule;
import bt.protocol.crypto.EncryptionPolicy;
import bt.runtime.BtClient;
import bt.runtime.BtRuntime;
import bt.runtime.Config;
import bt.torrent.selector.PieceSelector;
import bt.torrent.selector.RarestFirstSelector;
import bt.torrent.selector.SequentialSelector;
import com.google.inject.Module;
import com.googlecode.lanterna.input.KeyStroke;
import joptsimple.OptionException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.Security;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;

public class CliClient  {

    private static final Logger LOGGER = LoggerFactory.getLogger(CliClient.class);

    static {
        registerLog4jShutdownHook();
    }

    public static void main(String[] args) throws IOException {

        Options options;
        try {
            options = Options.parse(args);
        } catch (OptionException e) {
            Options.printHelp(System.out);
            return;
        }

        new CliClient(options).resume();
    }

    private Options options;

    private BtRuntime runtime;
    private BtClient client;
    private Optional<SessionStatePrinter> printer;

    private boolean running;

    private CliClient(Options options) {
        this.options = options;

        configureLogging(options.getLogLevel());
        configureSecurity();

        Collection<KeyStrokeBinding> keyBindings = Collections.singletonList(
                new KeyStrokeBinding(KeyStroke.fromString("p"), this::togglePause));

        Optional<InetAddress> acceptorAddressOverride = getAcceptorAddressOverride();
        Optional<Integer> portOverride = getPortOverride();

        Config config = new Config() {
            @Override
            public InetAddress getAcceptorAddress() {
                return acceptorAddressOverride.orElseGet(super::getAcceptorAddress);
            }

            @Override
            public int getAcceptorPort() {
                return portOverride.orElseGet(super::getAcceptorPort);
            }

            @Override
            public int getNumOfHashingThreads() {
                return Runtime.getRuntime().availableProcessors();
            }

            @Override
            public EncryptionPolicy getEncryptionPolicy() {
                return options.enforceEncryption()? EncryptionPolicy.REQUIRE_ENCRYPTED : EncryptionPolicy.PREFER_PLAINTEXT;
            }
        };

        Module dhtModule = new DHTModule(new DHTConfig() {
            @Override
            public boolean shouldUseRouterBootstrap() {
                return true;
            }
        });

        this.runtime = BtRuntime.builder(config)
                .module(dhtModule)
                .autoLoadModules()
                .disableAutomaticShutdown()
                .build();

        Storage storage = new FileSystemStorage(options.getTargetDirectory());
        PieceSelector selector = options.downloadSequentially() ?
                SequentialSelector.sequential() : RarestFirstSelector.randomizedRarest();

        BtClientBuilder clientBuilder = Bt.client(runtime)
                .storage(storage)
                .selector(selector);

        SessionStatePrinter printer = options.shouldDisableUi() ?
                null : SessionStatePrinter.createKeyInputAwarePrinter(keyBindings);
        if (!options.shouldDownloadAllFiles()) {
            if (printer == null) {
                clientBuilder.fileSelector(new CliFileSelector());
            } else {
                clientBuilder.fileSelector(new CliFileSelector(printer));
            }
        }
        if (printer != null) {
            clientBuilder.afterTorrentFetched(printer::setTorrent);
        }

        if (options.getMetainfoFile() != null) {
            clientBuilder = clientBuilder.torrent(toUrl(options.getMetainfoFile()));
        } else if (options.getMagnetUri() != null) {
            clientBuilder = clientBuilder.magnet(options.getMagnetUri());
        } else {
            throw new IllegalStateException("Torrent file or magnet URI is required");
        }

        this.client = clientBuilder.build();
        this.printer = Optional.ofNullable(printer);
    }

    private Optional<Integer> getPortOverride() {
        Integer port = options.getPort();
        if (port == null) {
            return Optional.empty();
        } else if (port < 1024 || port >= 65535) {
            throw new IllegalArgumentException("Invalid port: " + port + "; expected 1024..65534");
        }
        return Optional.of(port);
    }

    private Optional<InetAddress> getAcceptorAddressOverride() {
        String iface = options.getIface();
        if (iface == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(InetAddress.getByName(iface));
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Failed to parse the acceptor's internet address", e);
        }
    }

    private void configureLogging(LogLevel logLevel) {
        Level log4jLogLevel;
        switch (Objects.requireNonNull(logLevel)) {
            case NORMAL: {
                log4jLogLevel = Level.INFO;
                break;
            }
            case VERBOSE: {
                log4jLogLevel = Level.DEBUG;
                break;
            }
            case TRACE: {
                log4jLogLevel = Level.TRACE;
                break;
            }
            default: {
                throw new IllegalArgumentException("Unknown log level: " + logLevel.name());
            }
        }
        Configurator.setLevel("bt", log4jLogLevel);
    }

    private void configureSecurity() {
        // Starting with JDK 8u152 this is a way to programmatically allow unlimited encryption
        // See http://www.oracle.com/technetwork/java/javase/8u152-relnotes-3850503.html
        String key = "crypto.policy";
        String value = "unlimited";
        try {
            Security.setProperty(key, value);
        } catch (Exception e) {
            LOGGER.error(String.format("Failed to set security property '%s' to '%s'", key, value), e);
        }
    }

    private static URL toUrl(File file) {
        try {
            return file.toURI().toURL();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Unexpected error", e);
        }
    }

    void resume() {
        if (running) {
            return;
        }

        running = true;
        try {
            client.startAsync(state -> {
                printer.ifPresent(p -> p.print(state));
                if (!options.shouldSeedAfterDownloaded() && state.getPiecesRemaining() == 0) {
                    runtime.shutdown();
                }
            }, 1000);
        } catch (Throwable e) {
            // in case the start request to the tracker fails
            printer.ifPresent(SessionStatePrinter::shutdown);
            printAndShutdown(e);
        }
    }

    void pause() {
        if (!running) {
            return;
        }

        try {
            client.stop();
        } catch (Throwable e) {
            LOGGER.warn("Unexpected error when stopping client", e);
        } finally {
            running = false;
        }
    }

    private void togglePause() {
        if (running) {
            pause();
        } else {
            resume();
        }
    }

    private static void printAndShutdown(Throwable e) {
        // ignore interruptions on shutdown
        if (!(e instanceof InterruptedException)) {
            LOGGER.error("Unexpected error, exiting...", e);
        }
        System.exit(1);
    }

    private static void registerLog4jShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                if( LogManager.getContext() instanceof LoggerContext) {
                    Configurator.shutdown((LoggerContext)LogManager.getContext());
                }
            }
        });
    }
}
