/*
 * Copyright (c) 2016—2021 Andrei Tomashpolskiy and individual contributors.
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

import bt.metainfo.TorrentFile;
import bt.torrent.fileselector.SelectionResult;
import bt.torrent.fileselector.TorrentFileSelector;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;

public class CliFileSelector extends TorrentFileSelector {
    private static final String PROMPT_MESSAGE_FORMAT = "Download '%s'? (Hit <Enter> to confirm, <Esc> to skip, <a> to confirm all remaining, <s> to skip all remaining or <q> to abort)";
    private static final String ILLEGAL_KEYPRESS_WARNING = "*** Invalid key pressed. ***";
    private static final int EOF = -1;
    private static final int ESC = 0x1B;
    private static final int CTRL_C = 3;
    private static final int CTRL_D = 4;

    private final Optional<SessionStatePrinter> printer;
    private volatile boolean shutdown;

    private boolean selectRemaining = false;
    private boolean skipRemaining = false;

    public CliFileSelector() {
        this.printer = Optional.empty();
        registerShutdownHook();
    }

    public CliFileSelector(SessionStatePrinter printer) {
        this.printer = Optional.of(printer);
        registerShutdownHook();
    }

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }

    @Override
    public List<SelectionResult> selectFiles(List<TorrentFile> files) {
        printer.ifPresent(SessionStatePrinter::pause);

        List<SelectionResult> results = super.selectFiles(files);

        printer.ifPresent(SessionStatePrinter::resume);
        return results;
    }

    @Override
    protected SelectionResult select(TorrentFile file) {
        while (!shutdown) {
            if (selectRemaining) {
                return SelectionResult.select().build();
            } else if (skipRemaining) {
                return SelectionResult.skip();
            }

            System.out.println(getPromptMessage(file));

            try {
                switch (System.in.read()) {
                    case EOF: {
                        throw new IllegalStateException("EOF");
                    }
                    case 'a':
                    case 'A': {
                        selectRemaining = true;
                        return SelectionResult.select().build();
                    }
                    case '\n': { // <Enter>
                        return SelectionResult.select().build();
                    }
                    case 's':
                    case 'S': {
                        skipRemaining = true;
                        return SelectionResult.skip();
                    }
                    case ESC: {
                        return SelectionResult.skip();
                    }
                    case CTRL_C:
                    case CTRL_D:
                    case 'q':
                    case 'Q': {
                        confirmAbort();
                        break;
                    }
                    default: {
                        System.out.println(ILLEGAL_KEYPRESS_WARNING);
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        throw new IllegalStateException("Shutdown");
    }

    private static String getPromptMessage(TorrentFile file) {
        return String.format(PROMPT_MESSAGE_FORMAT, String.join("/", file.getPathElements()));
    }

    private void shutdown() {
        this.shutdown = true;
    }

    private void confirmAbort() throws IOException {
        System.out.println("Are you sure you want to abort (y/n)?");
        switch (System.in.read()) {
            case 'y':
            case 'Y':
            case CTRL_C:
            case CTRL_D: {
                System.out.println("Aborting...");
                System.exit(0);
            }
            default: {
                System.out.println("Resuming...");
            }
        }
    }
}
