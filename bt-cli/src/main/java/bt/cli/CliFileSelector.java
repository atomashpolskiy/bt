/*
 * Copyright (c) 2016—2018 Andrei Tomashpolskiy and individual contributors.
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
import java.util.List;
import java.util.Optional;

public class CliFileSelector extends TorrentFileSelector {
    private static final String PROMPT_MESSAGE_FORMAT = "Download '%s'? (hit <Enter> to confirm or <Esc> to skip)";
    private static final String ILLEGAL_KEYPRESS_WARNING = "*** Invalid key pressed. Please, use only <Enter> or <Esc> ***";

    private final Optional<SessionStatePrinter> printer;
    private volatile boolean shutdown;

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
            System.out.println(getPromptMessage(file));

            try {
                switch (System.in.read()) {
                    case -1: {
                        throw new IllegalStateException("EOF");
                    }
                    case '\n': { // <Enter>
                        return SelectionResult.select().build();
                    }
                    case 0x1B: { // <Esc>
                        return SelectionResult.skip();
                    }
                    default: {
                        System.out.println(ILLEGAL_KEYPRESS_WARNING);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
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
}
