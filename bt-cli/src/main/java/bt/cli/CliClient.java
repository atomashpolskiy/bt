package bt.cli;

import bt.Bt;
import bt.data.Storage;
import bt.data.file.FileSystemStorage;
import bt.peerexchange.PeerExchangeModule;
import bt.runtime.BtClient;
import bt.runtime.BtRuntime;
import bt.tracker.http.HttpTrackerModule;
import joptsimple.OptionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

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

        new CliClient().runWithOptions(options);
    }

    void runWithOptions(Options options) {

        Storage storage = new FileSystemStorage(options.getTargetDirectory());

        BtRuntime runtime = BtRuntime.builder()
                .module(new PeerExchangeModule())
                .module(new HttpTrackerModule())
                .build();

        BtClient client = Bt.client(storage)
                .url(toUrl(options.getMetainfoFile()))
                .attachToRuntime(runtime);

        SessionStatePrinter printer = SessionStatePrinter.createKeyInputAwarePrinter(client.getSession().getTorrent());
        try {
            client.startAsync(state -> {
                printer.print(state);
                if (!options.shouldSeedAfterDownloaded() && state.getPiecesRemaining() == 0) {
                    client.stop();
                }
            }, 1000).join();

        } catch (Exception e) {
            // in case the start request to the tracker fails
            printer.shutdown();
            printAndShutdown(e);
        }
    }

    private static URL toUrl(File file) {
        try {
            return file.toURI().toURL();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Unexpected error", e);
        }
    }

    private static void printAndShutdown(Throwable e) {
        LOGGER.error("Unexpected error, exiting...", e);
        System.exit(1);
    }
}
