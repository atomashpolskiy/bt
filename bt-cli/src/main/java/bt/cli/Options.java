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

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

public class Options {

    public enum LogLevel {
        NORMAL, VERBOSE, TRACE
    }

    private static final OptionSpec<File> metainfoFileOptionSpec;
    private static final OptionSpec<String> magnetUriOptionSpec;
    private static final OptionSpec<File> targetDirectoryOptionSpec;
    private static final OptionSpec<Void> shouldSeedOptionSpec;
    private static final OptionSpec<Void> sequentialOptionSpec;
    private static final OptionSpec<Void> enforceEncryptionOptionSpec;
    private static final OptionSpec<Void> headlessOptionSpec;
    private static final OptionSpec<Void> verboseOptionSpec;
    private static final OptionSpec<Void> traceOptionSpec;
    private static final OptionSpec<String> ifaceOptionSpec;
    private static final OptionSpec<Integer> torrentPortOptionSpec;
    private static final OptionSpec<Void> shouldDownloadAllFiles;

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

        verboseOptionSpec = parser.acceptsAll(Arrays.asList("v", "verbose"), "Enable more verbose logging");

        traceOptionSpec = parser.accepts("trace", "Enable trace logging");

        ifaceOptionSpec = parser.acceptsAll(Arrays.asList("i", "iface"), "Use specific network interface")
                .withRequiredArg().ofType(String.class);

        torrentPortOptionSpec = parser.acceptsAll(Arrays.asList("p", "port"), "Listen on specific port for incoming connections")
                .withRequiredArg().ofType(Integer.class);

        shouldDownloadAllFiles = parser.acceptsAll(Arrays.asList("a", "all"), "Download all files (file selection will be disabled)");
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
                opts.has(headlessOptionSpec),
                opts.has(verboseOptionSpec),
                opts.has(traceOptionSpec),
                opts.valueOf(ifaceOptionSpec),
                opts.valueOf(torrentPortOptionSpec),
                opts.has(shouldDownloadAllFiles));
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
    private boolean verboseLogging;
    private boolean traceLogging;
    private String iface;
    private Integer port;
    private boolean downloadAllFiles;

    public Options(File metainfoFile,
                   String magnetUri,
                   File targetDirectory,
                   boolean seedAfterDownloaded,
                   boolean sequential,
                   boolean enforceEncryption,
                   boolean disableUi,
                   boolean verboseLogging,
                   boolean traceLogging,
                   String iface,
                   Integer port,
                   boolean downloadAllFiles) {
        this.metainfoFile = metainfoFile;
        this.magnetUri = magnetUri;
        this.targetDirectory = targetDirectory;
        this.seedAfterDownloaded = seedAfterDownloaded;
        this.sequential = sequential;
        this.enforceEncryption = enforceEncryption;
        this.disableUi = disableUi;
        this.verboseLogging = verboseLogging;
        this.traceLogging = traceLogging;
        this.iface = iface;
        this.port = port;
        this.downloadAllFiles = downloadAllFiles;
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

    public LogLevel getLogLevel() {
        return traceLogging ? LogLevel.TRACE : (verboseLogging ? LogLevel.VERBOSE : LogLevel.NORMAL);
    }

    public String getIface() {
        return iface;
    }

    public Integer getPort() {
        return port;
    }

    public boolean shouldDownloadAllFiles() {
        return downloadAllFiles;
    }
}
