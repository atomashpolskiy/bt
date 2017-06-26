package bt.cli;

import bt.Bt;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;

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
    private SessionStatePrinter printer;

    private boolean running;

    private CliClient(Options options) {
        this.options = options;

        Collection<KeyStrokeBinding> keyBindings = Collections.singletonList(
                new KeyStrokeBinding(KeyStroke.fromString("p"), this::togglePause));

        Config config = new Config() {
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

        this.client = Bt.client(runtime)
                .storage(storage)
                .torrent(toUrl(options.getMetainfoFile()))
                .selector(selector)
                .build();

//        this.printer = SessionStatePrinter.createKeyInputAwarePrinter(
//                client.getSession().getTorrent(), keyBindings);
    }

    void resume() {
        if (running) {
            return;
        }

        running = true;
        try {
            client.startAsync(state -> {
                printer.print(state);
                if (!options.shouldSeedAfterDownloaded() && state.getPiecesRemaining() == 0) {
                    runtime.shutdown();
                }
            }, 1000);
        } catch (Throwable e) {
            // in case the start request to the tracker fails
            printer.shutdown();
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

    private static URL toUrl(File file) {
        try {
            return file.toURI().toURL();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Unexpected error", e);
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
