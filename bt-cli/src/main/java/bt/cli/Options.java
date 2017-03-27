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
    private static final OptionSpec<File> targetDirectoryOptionSpec;
    private static final OptionSpec<Void> shouldSeedOptionSpec;
    private static final OptionSpec<Void> sequentialOptionSpec;

    private static final OptionParser parser;

    static {
        parser = new OptionParser() {
            {
                acceptsAll(Arrays.asList("?", "h", "help")).isForHelp();
            }
        };
        metainfoFileOptionSpec = parser.acceptsAll(Arrays.asList("f", "file"), "Torrent metainfo file")
                .withRequiredArg().ofType(File.class)
                .required();

        targetDirectoryOptionSpec = parser.acceptsAll(Arrays.asList("d", "dir"), "Target download location")
                .withRequiredArg().ofType(File.class)
                .required();

        shouldSeedOptionSpec = parser.acceptsAll(Arrays.asList("s", "seed"), "Continue to seed when download is complete");

        sequentialOptionSpec = parser.acceptsAll(Arrays.asList("S", "sequential"), "Download sequentially");
    }

    /**
     * @throws OptionException
     */
    public static Options parse(String... args) {
        OptionSet opts = parser.parse(args);
        return new Options(
                opts.valueOf(metainfoFileOptionSpec),
                opts.valueOf(targetDirectoryOptionSpec),
                opts.has(shouldSeedOptionSpec),
                opts.has(sequentialOptionSpec));
    }

    public static void printHelp(OutputStream out) {
        try {
            parser.printHelpOn(out);
        } catch (IOException e) {
            throw new RuntimeException("Can't write help to out", e);
        }
    }

    private File metainfoFile;
    private File targetDirectory;
    private boolean seedAfterDownloaded;
    private boolean sequential;

    public Options(File metainfoFile, File targetDirectory, boolean seedAfterDownloaded, boolean sequential) {
        this.metainfoFile = metainfoFile;
        this.targetDirectory = targetDirectory;
        this.seedAfterDownloaded = seedAfterDownloaded;
        this.sequential = sequential;
    }

    public File getMetainfoFile() {
        return metainfoFile;
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
}
