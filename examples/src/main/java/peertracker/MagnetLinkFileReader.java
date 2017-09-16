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

package peertracker;

import bt.magnet.MagnetUri;
import bt.magnet.MagnetUriParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class MagnetLinkFileReader {
    private static final Logger LOGGER = LoggerFactory.getLogger(MagnetLinkFileReader.class);

    public Collection<MagnetUri> readFromFile(String path) {

        // explicitly convert to magnet objects to filter out unparseable garbage
        MagnetUriParser parser = MagnetUriParser.lenientParser();

        return readLines(path).stream()
            .map(line -> {
                Optional<MagnetUri> uri = Optional.empty();
                try {
                    uri = Optional.of(parser.parse(line));
                } catch (Exception e) {
                    LOGGER.error("Failed to parse magnet link: " + line, e);
                }
                return uri;
            })
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    }

    private Collection<String> readLines(String pathToFile) {
        if (pathToFile == null) {
            LOGGER.error("File with magnets was not provided");
            System.exit(-1);
        }

        // read and de-duplicate
        Set<String> lines = new HashSet<>();
        try (InputStream in = new FileInputStream(new File(pathToFile))) {
            BufferedReader r = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = r.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    lines.add(trimmed);
                }
            }
        } catch (IOException e) {
            LOGGER.error("I/O exception when reading file with magnets: " + pathToFile, e);
            System.exit(-1);
        }
        return lines;
    }
}
