package bt.cli;

import bt.metainfo.Torrent;
import bt.net.Peer;
import bt.torrent.TorrentSessionState;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;

public class SessionStatePrinter {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionStatePrinter.class);

    private static final String TORRENT_INFO = "Downloading %s (%,d B)";
    private static final String SESSION_INFO = "Peers: %2d\t\tDown: %4.1f %s/s\t\tUp: %4.1f %s/s\t\t";
    private static final String DURATION_INFO ="Elapsed time: %s\t\tRemaining time: %s";

    private static final String WHITESPACES = "\u0020\u0020\u0020\u0020\u0020\u0020\u0020\u0020\u0020\u0020\u0020";

    public static SessionStatePrinter createKeyInputAwarePrinter(Torrent torrent) {

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
                            LOGGER.error("Unexpected error when reading user input", e);
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

    private Screen screen;
    private TextGraphics graphics;

    private volatile boolean shutdown;

    private Torrent torrent;
    private volatile long started;
    private volatile long downloaded;
    private volatile long uploaded;

    public SessionStatePrinter(Torrent torrent) {
        try {
            Terminal terminal = new DefaultTerminalFactory(System.out, System.in,
                     Charset.forName("UTF-8")).createTerminal();
            terminal.setCursorVisible(false);

            screen = new TerminalScreen(terminal);
            graphics = screen.newTextGraphics();
            screen.startScreen();
            screen.clear();

            started = System.currentTimeMillis();

            this.torrent = torrent;
            printTorrentInfo(torrent);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create terminal", e);
        }
    }

    public boolean isShutdown() {
        return shutdown;
    }

    public void shutdown() {

        if (shutdown) {
            return;
        }

        try {
            screen.clear();
            screen.stopScreen();
        } catch (Throwable e) {
            // ignore
        } finally {
            shutdown = true;
        }
    }

    /**
     * blocking
     */
    public KeyStroke readKeyInput() throws IOException {
        return screen.readInput();
    }

    private void printTorrentInfo(Torrent torrent) {
        graphics.putString(0, 0, String.format(TORRENT_INFO, torrent.getName(), torrent.getSize()));
        char[] chars = new char[graphics.getSize().getColumns()];
        Arrays.fill(chars, '-');
        graphics.putString(0, 1, String.valueOf(chars));
    }

    /**
     * call me once per second
     */
    public void print(TorrentSessionState sessionState) {

        if (shutdown) {
            return;
        }

        try {
            long downloaded = sessionState.getDownloaded();
            long uploaded = sessionState.getUploaded();

            graphics.putString(0, 2, getDurations(downloaded - this.downloaded,
                    sessionState.getPiecesRemaining(), sessionState.getPiecesTotal()));

            graphics.putString(0, 3, getSessionInfo(sessionState.getConnectedPeers(),
                    downloaded - this.downloaded, uploaded - this.uploaded));
            graphics.putString(0, 4, getProgressBar(sessionState.getPiecesTotal(), sessionState.getPiecesRemaining()));

            if (sessionState.getPiecesRemaining() == 0) {
                graphics.putString(0, 5, "Download is complete. Press Ctrl-C to stop seeding and exit.");
            }

            screen.refresh(Screen.RefreshType.DELTA);

            this.downloaded = downloaded;
            this.uploaded = uploaded;

        } catch (Throwable e) {
            LOGGER.error("Unexpected error when printing session state", e);
            shutdown();
        }
    }

    private String getSessionInfo(Set<Peer> peers, long downloaded, long uploaded) {

        Rate downRate = new Rate(downloaded);
        Rate upRate = new Rate(uploaded);

        return String.format(SESSION_INFO, peers.size(),
                downRate.getQuantity(), downRate.getMeasureUnit(),
                upRate.getQuantity(), upRate.getMeasureUnit());
    }

    private String getDurations(long downloaded, int piecesRemaining, int piecesTotal) {

        Duration elapsed = Duration.ofMillis(System.currentTimeMillis() - started);
        String elapsedStr = formatDuration(elapsed);

        String remainingStr;
        if (piecesRemaining == 0) {
            remainingStr = "-" + WHITESPACES;
        } else if (downloaded == 0) {
            remainingStr = "\u221E" + WHITESPACES; // infinity
        } else {
            long size = torrent.getSize();
            double remaining = piecesRemaining / ((double) piecesTotal);
            long remainingBytes = (long) (size * remaining);
            Duration remainingTime = Duration.ofSeconds(remainingBytes / downloaded);
            // overwrite trailing chars with whitespaces if there are any
            remainingStr = formatDuration(remainingTime) + WHITESPACES;
        }
        return String.format(DURATION_INFO, elapsedStr, remainingStr);
    }

    private static String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        long absSeconds = Math.abs(seconds);
        String positive = String.format("%d:%02d:%02d", absSeconds / 3600, (absSeconds % 3600) / 60, absSeconds % 60);
        return seconds < 0 ? "-" + positive : positive;
    }

    private String getProgressBar(int total, int remaining) throws IOException {

        int complete = (int) ((total - remaining) / ((double) total) * 100);

        int width = graphics.getSize().getColumns() - 25;
        if (width < 0) {
            return "Progress: " + complete + "%";
        }

        String s = "Progress: [%-" + width + "s] %d%%";
        double shrinkFactor = width / 100d;
        char[] chars = new char[(int) (complete * shrinkFactor)];
        Arrays.fill(chars, '#');
        return String.format(s, String.valueOf(chars), complete);
    }

    private static class Rate {

        private long bytes;
        private double quantity;
        private String measureUnit;

        Rate(long delta) {
            if (delta < 0) {
                throw new IllegalArgumentException("delta: " + delta);
            } else if (delta < (2 << 9)) {
                quantity = delta;
                measureUnit = "B";
            } else if (delta < (2 << 19)) {
                quantity = delta / (2 << 9);
                measureUnit = "KB";
            } else {
                quantity = ((double) delta) / (2 << 19);
                measureUnit = "MB";
            }
            bytes = delta;
        }

        public long getBytes() {
            return bytes;
        }

        public double getQuantity() {
            return quantity;
        }

        public String getMeasureUnit() {
            return measureUnit;
        }
    }
}
