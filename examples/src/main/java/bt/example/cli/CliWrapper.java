package bt.example.cli;

import bt.Bt;
import bt.BtClient;
import bt.BtRuntime;
import bt.BtRuntimeBuilder;
import bt.data.DataAccessFactory;
import bt.data.file.FileSystemDataAccessFactory;
import bt.metainfo.Torrent;
import bt.module.PeerExchangeModule;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.nhl.bootique.cli.Cli;
import com.nhl.bootique.command.CommandMetadata;
import com.nhl.bootique.command.CommandOutcome;
import com.nhl.bootique.command.CommandWithMetadata;
import joptsimple.OptionException;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

public class CliWrapper extends CommandWithMetadata {

    public static void main(String[] args) throws IOException {

        Options options;
        try {
            options = Options.parse(args);
        } catch (OptionException e) {
            Options.printHelp(System.out);
            return;
        }

        new CliWrapper().runWithOptions(options);
    }

    private void runWithOptions(Options options) {

        DataAccessFactory dataAccess = new FileSystemDataAccessFactory(options.getTargetDirectory());

        BtRuntime runtime = BtRuntimeBuilder.builder()
                .module(new PeerExchangeModule())
                .build();

        BtClient client = Bt.client(runtime)
                .url(toUrl(options.getMetainfoFile()))
                .build(dataAccess);

        SessionStatePrinter printer = createPrinter(client.getSession().getTorrent());
        try {
            client.startAsync(state -> {
                printer.print(state);
                if (!options.shouldSeedAfterDownloaded() && state.getPiecesRemaining() == 0) {
                    client.stop();
                }
            }, 1000).thenRun(runtime::shutdown).join();

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

    private static SessionStatePrinter createPrinter(Torrent torrent) {

        return new SessionStatePrinter(torrent) {

            private Thread t;

            {
                t = new Thread(() -> {
                    while (!isShutdown()) {
                        try {
                            KeyStroke keyStroke = readKeyInput();
                            if (keyStroke.isCtrlDown() && keyStroke.getKeyType() == KeyType.Character
                                    && keyStroke.getCharacter().equals('c')) {
                                shutdown();
                                System.exit(0);
                            }
                        } catch (Throwable e) {
                            e.printStackTrace(System.out);
                            System.out.flush();
                        }
                    }
                });
                t.setDaemon(true);
                t.start();

                Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
            }

            @Override
            public void shutdown() {
                if (!isShutdown()) {
                    super.shutdown();
                    t.interrupt();
                }
            }
        };
    }

    private static void printAndShutdown(Throwable e) {
        e.printStackTrace(System.out);
        System.out.flush();
        System.exit(1);
    }

    public CliWrapper() {
        super(createMetadata());
    }

    private static CommandMetadata createMetadata() {
        return CommandMetadata.builder(CliWrapper.class)
				.description("Simple CLI wrapper")
				.build();
    }

    @Override
    public CommandOutcome run(Cli cli) {

        List<String> argsList = cli.standaloneArguments();
        String[] args = argsList.toArray(new String[argsList.size()]);

        Options options;
        try {
            options = Options.parse(args);
        } catch (OptionException e) {
            Options.printHelp(System.out);
            return CommandOutcome.failed(2, "Illegal arguments: " + Arrays.toString(args));
        }

        runWithOptions(options);
        return CommandOutcome.succeeded();
    }
}
