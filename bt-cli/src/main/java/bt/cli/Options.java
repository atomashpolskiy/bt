package bt.cli;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

public class Options {

    private static final OptionSpec<File> metainfoFileOptionSpec;
    private static final OptionSpec<String> magnetUriOptionSpec;
    private static final OptionSpec<File> targetDirectoryOptionSpec;
    private static final OptionSpec<Void> shouldSeedOptionSpec;
    private static final OptionSpec<Void> sequentialOptionSpec;
    private static final OptionSpec<Void> enforceEncryptionOptionSpec;
    private static final OptionSpec<Void> headlessOptionSpec;

    private static final OptionParser parser;

    static {
        parser = new OptionParser() {
            {
                acceptsAll(Arrays.asList("?", "h", "help")).isForHelp();
            }
        };
        metainfoFileOptionSpec = parser.acceptsAll(Arrays.asList("f", "file"), "Torrent metainfo file")
                .withRequiredArg().ofType(File.class);

        magnetUriOptionSpec = parser.acceptsAll(Arrays.asList("m", "magnet"), "Magnet URI")
                .withRequiredArg().ofType(String.class);

        targetDirectoryOptionSpec = parser.acceptsAll(Arrays.asList("d", "dir"), "Target download location")
                .withRequiredArg().ofType(File.class)
                .required();

        shouldSeedOptionSpec = parser.acceptsAll(Arrays.asList("s", "seed"), "Continue to seed when download is complete");

        sequentialOptionSpec = parser.acceptsAll(Arrays.asList("S", "sequential"), "Download sequentially");

        enforceEncryptionOptionSpec = parser.acceptsAll(Arrays.asList("e", "encrypted"), "Enforce encryption for all connections");

        headlessOptionSpec = parser.acceptsAll(Arrays.asList("H", "headless"), "Disable UI");
    }

    /**
     * @throws OptionException
     */
    public static Options parse(String... args) {
        OptionSet opts = parser.parse(args);
        return new Options(
                opts.valueOf(metainfoFileOptionSpec),
                opts.valueOf(magnetUriOptionSpec),
                opts.valueOf(targetDirectoryOptionSpec),
                opts.has(shouldSeedOptionSpec),
                opts.has(sequentialOptionSpec),
                opts.has(enforceEncryptionOptionSpec),
                opts.has(headlessOptionSpec));
    }

    public static void printHelp(OutputStream out) {
        try {
            parser.printHelpOn(out);
        } catch (IOException e) {
            throw new RuntimeException("Can't write help to out", e);
        }
    }

    private File metainfoFile;
    private String magnetUri;
    private File targetDirectory;
    private boolean seedAfterDownloaded;
    private boolean sequential;
    private boolean enforceEncryption;
    private boolean disableUi;

    public Options(File metainfoFile,
                   String magnetUri,
                   File targetDirectory,
                   boolean seedAfterDownloaded,
                   boolean sequential,
                   boolean enforceEncryption,
                   boolean disableUi) {
        this.metainfoFile = metainfoFile;
        this.magnetUri = magnetUri;
        this.targetDirectory = targetDirectory;
        this.seedAfterDownloaded = seedAfterDownloaded;
        this.sequential = sequential;
        this.enforceEncryption = enforceEncryption;
        this.disableUi = disableUi;
    }

    public File getMetainfoFile() {
        return metainfoFile;
    }

    public String getMagnetUri() {
        return magnetUri;
    }

    public File getTargetDirectory() {
        return targetDirectory;
    }

    public boolean shouldSeedAfterDownloaded() {
        return seedAfterDownloaded;
    }

    public boolean downloadSequentially() {
        return sequential;
    }

    public boolean enforceEncryption() {
        return enforceEncryption;
    }

    public boolean shouldDisableUi() {
        return disableUi;
    }
}
