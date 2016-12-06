package bt.cli;

import bt.Bt;
import bt.data.Storage;
import bt.data.file.FileSystemStorage;
import bt.peerexchange.PeerExchangeModule;
import bt.runtime.BtClient;
import bt.runtime.BtRuntime;
import bt.tracker.http.HttpTrackerModule;
import com.googlecode.lanterna.input.KeyStroke;
import joptsimple.OptionException;
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

    private BtClient client;
    private SessionStatePrinter printer;

    private boolean running;

    private CliClient(Options options) {
        this.options = options;

        Collection<KeyStrokeBinding> keyBindings = Collections.singletonList(
                new KeyStrokeBinding(KeyStroke.fromString("p"), this::togglePause));

        BtRuntime runtime = BtRuntime.builder()
                .module(new PeerExchangeModule())
                .module(new HttpTrackerModule())
                .disableAutomaticShutdown()
                .build();

        Storage storage = new FileSystemStorage(options.getTargetDirectory());

        this.client = Bt.client(storage)
                .url(toUrl(options.getMetainfoFile()))
                .attachToRuntime(runtime);

        this.printer = SessionStatePrinter.createKeyInputAwarePrinter(
                client.getSession().getTorrent(), keyBindings);
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
                    client.stop();
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
        LOGGER.error("Unexpected error, exiting...", e);
        System.exit(1);
    }
}
