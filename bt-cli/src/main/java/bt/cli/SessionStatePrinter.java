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

import bt.metainfo.Torrent;
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
import java.util.Collection;
import java.util.Optional;

public class SessionStatePrinter {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionStatePrinter.class);

    private static final String TORRENT_INFO = "Downloading %s (%,d B)";
    private static final String DURATION_INFO ="Elapsed time: %s\t\tRemaining time: %s";
    private static final String RATE_FORMAT = "%4.1f %s/s";
    private static final String SESSION_INFO = "Peers: %2d\t\tDown: " + RATE_FORMAT + "\t\tUp: " + RATE_FORMAT + "\t\t";

    private static final String WHITESPACES = "\u0020\u0020\u0020\u0020\u0020\u0020\u0020\u0020\u0020\u0020\u0020";

    private static final String LOG_ENTRY = "Downloading.. Peers: %s; Down: " + RATE_FORMAT +
            "; Up: " + RATE_FORMAT + "; %.2f%% complete; Remaining time: %s";

    private static final String LOG_ENTRY_SEED = "Seeding.. Peers: %s; Up: " + RATE_FORMAT;

    public static SessionStatePrinter createKeyInputAwarePrinter(Collection<KeyStrokeBinding> bindings) {
        return new SessionStatePrinter() {
            private Thread t;

            {
                t = new Thread(() -> {
                    while (!isShutdown()) {
                        try {
                            // don't intercept input when paused
                            if (super.supressOutput) {
                                Thread.sleep(1000);
                                continue;
                            }

                            KeyStroke keyStroke = pollKeyInput();
                            if (keyStroke == null) {
                                Thread.sleep(100);
                            } else if (keyStroke.isCtrlDown() && keyStroke.getKeyType() == KeyType.Character
                                    && keyStroke.getCharacter().equals('c')) {
                                shutdown();
                                System.exit(0);
                            } else {
                                bindings.forEach(binding -> {
                                    if (keyStroke.equals(binding.getKeyStroke())) {
                                        binding.getBinding().run();
                                    }
                                });
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

    private volatile boolean supressOutput;
    private volatile boolean shutdown;

    private Optional<Torrent> torrent;
    private volatile long started;
    private volatile long downloaded;
    private volatile long uploaded;

    public SessionStatePrinter() {
        try {
            Terminal terminal = new DefaultTerminalFactory(System.out, System.in,
                     Charset.forName("UTF-8")).createTerminal();
            terminal.setCursorVisible(false);

            screen = new TerminalScreen(terminal);
            graphics = screen.newTextGraphics();
            screen.startScreen();
            clearScreen();

            started = System.currentTimeMillis();

            this.torrent = Optional.empty();
            printTorrentInfo();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create terminal", e);
        }
    }

    public void setTorrent(Torrent torrent) {
        this.torrent = Optional.of(torrent);
    }

    public boolean isShutdown() {
        return shutdown;
    }

    public synchronized void shutdown() {
        if (shutdown) {
            return;
        }

        try {
            clearScreen();
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

    /**
     * non-blocking
     */
    public KeyStroke pollKeyInput() throws IOException {
        return screen.pollInput();
    }

    private void printTorrentInfo() {
        printTorrentNameAndSize(torrent);
        char[] chars = new char[graphics.getSize().getColumns()];
        Arrays.fill(chars, '-');
        graphics.putString(0, 1, String.valueOf(chars));
    }

    private void printTorrentNameAndSize(Optional<Torrent> torrent) {
        String name = torrent.isPresent() ? torrent.get().getName() : "";
        long size = torrent.isPresent() ? torrent.get().getSize() : 0;

        graphics.putString(0, 0, String.format(TORRENT_INFO, name, size));
    }

    /**
     * call me once per second
     */
    public synchronized void print(TorrentSessionState sessionState) {
        if (supressOutput || shutdown) {
            return;
        }

        try {
            printTorrentInfo();

            long downloaded = sessionState.getDownloaded();
            long uploaded = sessionState.getUploaded();

            printTorrentNameAndSize(torrent);

            String elapsedTime = getElapsedTime();
            String remainingTime = getRemainingTime(downloaded - this.downloaded,
                    sessionState.getPiecesRemaining(), sessionState.getPiecesNotSkipped());
            graphics.putString(0, 2, String.format(DURATION_INFO, elapsedTime, remainingTime));

            Rate downRate = new Rate(downloaded - this.downloaded);
            Rate upRate = new Rate(uploaded - this.uploaded);
            int peerCount = sessionState.getConnectedPeers().size();
            String sessionInfo = String.format(SESSION_INFO, peerCount, downRate.getQuantity(), downRate.getMeasureUnit(),
                upRate.getQuantity(), upRate.getMeasureUnit());
            graphics.putString(0, 3, sessionInfo);

            int completed = sessionState.getPiecesComplete();
            double completePercents = getCompletePercentage(sessionState.getPiecesTotal(), completed);
            double requiredPercents = getTargetPercentage(sessionState.getPiecesTotal(), completed, sessionState.getPiecesRemaining());
            graphics.putString(0, 4, getProgressBar(completePercents, requiredPercents));

            boolean complete = (sessionState.getPiecesRemaining() == 0);
            if (complete) {
                graphics.putString(0, 5, "Download is complete. Press Ctrl-C to stop seeding and exit.");
            }

            // might use RefreshType.DELTA, but it does not tolerate resizing of the window
            screen.refresh(Screen.RefreshType.COMPLETE);

            if (LOGGER.isDebugEnabled()) {
                if (complete) {
                    LOGGER.debug(String.format(LOG_ENTRY_SEED, peerCount, upRate.getQuantity(), upRate.getMeasureUnit()));
                } else {
                    LOGGER.debug(String.format(LOG_ENTRY, peerCount, downRate.getQuantity(), downRate.getMeasureUnit(),
                            upRate.getQuantity(), upRate.getMeasureUnit(), completePercents, remainingTime));
                }
            }

            this.downloaded = downloaded;
            this.uploaded = uploaded;

        } catch (Throwable e) {
            LOGGER.error("Unexpected error when printing session state", e);
            shutdown();
        }
    }

    private String getElapsedTime() {
        Duration elapsed = Duration.ofMillis(System.currentTimeMillis() - started);
        return formatDuration(elapsed);
    }

    private String getRemainingTime(long downloaded, int piecesRemaining, int piecesTotal) {
        String remainingStr;
        if (piecesRemaining == 0) {
            remainingStr = "-" + WHITESPACES;
        } else if (downloaded == 0 || !torrent.isPresent()) {
            remainingStr = "\u221E" + WHITESPACES; // infinity
        } else {
            long size = torrent.get().getSize();
            double remaining = piecesRemaining / ((double) piecesTotal);
            long remainingBytes = (long) (size * remaining);
            Duration remainingTime = Duration.ofSeconds(remainingBytes / downloaded);
            // overwrite trailing chars with whitespaces if there are any
            remainingStr = formatDuration(remainingTime) + WHITESPACES;
        }
        return remainingStr;
    }

    private static String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        long absSeconds = Math.abs(seconds);
        String positive = String.format("%d:%02d:%02d", absSeconds / 3600, (absSeconds % 3600) / 60, absSeconds % 60);
        return seconds < 0 ? "-" + positive : positive;
    }

    private String getProgressBar(double completePercents, double requiredPercents) throws IOException {
        int completeInt = (int) completePercents;
        int requiredInt = (int) requiredPercents;

        int width = graphics.getSize().getColumns() - 25;
        if (width < 0) {
            return "Progress: " + completeInt + "% (req.: " + requiredInt + "%)";
        }

        String s = "Progress: [%-" + width + "s] %d%%";
        char[] bar = new char[width];
        double shrinkFactor = width / 100d;
        int bound = (int) (completeInt * shrinkFactor);
        Arrays.fill(bar, 0, bound, '#');
        Arrays.fill(bar, bound, bar.length, ' ');
        if (completeInt != requiredInt) {
            bar[(int) (requiredInt * shrinkFactor) - 1] = '|';
        }
        return String.format(s, String.valueOf(bar), completeInt);
    }

    private double getCompletePercentage(int total, int completed) {
        return completed / ((double) total) * 100;
    }

    private double getTargetPercentage(int total, int completed, int remaining) {
        return (completed + remaining) / ((double) total) * 100;
    }

    private void clearScreen() {
        try {
            this.screen.clear();
            this.screen.refresh();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized void pause() {
        if (supressOutput) {
            return;
        }
        this.supressOutput = true;
        clearScreen();
    }

    public synchronized void resume() {
        if (!supressOutput) {
            return;
        }
        this.supressOutput = false;
        clearScreen();
    }

    private static class Rate {

        private long bytes;
        private double quantity;
        private String measureUnit;

        Rate(long delta) {
            if (delta < 0) {
//                throw new IllegalArgumentException("delta: " + delta);
                // TODO: this is a workaround for some nasty bug in the session state,
                // due to which the delta is sometimes (very seldom) negative
                // To not crash the UI let's just skip the problematic 'tick' and pretend that nothing was received
                // instead of throwing an exception
                LOGGER.warn("Negative delta: " + delta + "; will not re-calculate rate");
                delta = 0;
                quantity = 0;
                measureUnit = "B";
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
