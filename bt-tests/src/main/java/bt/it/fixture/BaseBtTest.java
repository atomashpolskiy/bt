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

package bt.it.fixture;

import bt.metainfo.MetadataService;
import bt.metainfo.Torrent;
import org.junit.BeforeClass;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.function.Supplier;

/**
 * Base class for Bt integration tests.
 *
 * @since 1.0
 */
public class BaseBtTest {

    // default file root on the default (normal) file system
    private static final Path TEST_ROOT = Paths.get("target", "it");

    // test resources (bundled with bt-tests jar)
    private static final String FILE_NAME = "file.txt";
    private static final URL FILE_URL = BaseBtTest.class.getResource(FILE_NAME);
    private static final URL METAINFO_URL = BaseBtTest.class.getResource(FILE_NAME + ".torrent");

    private static byte[] SINGLE_FILE_CONTENT;

    @BeforeClass
    public static void setUpClass() {
        try {
            File singleFile = new File(FILE_URL.toURI());
            byte[] content = new byte[(int) singleFile.length()];
            try (FileInputStream fin = new FileInputStream(singleFile)) {
                fin.read(content);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read file: " + singleFile.getPath(), e);
            }
            SINGLE_FILE_CONTENT = content;
        } catch (URISyntaxException e) {
            throw new RuntimeException("Unexpected error", e);
        }
    }

    /**
     * Create a swarm builder.
     *
     * @return Swarm builder
     * @since 1.0
     */
    protected SwarmBuilder buildSwarm() {
        SwarmBuilder builder = new SwarmBuilder(getTestName(), TEST_ROOT, getSingleFile());
        builder.module(new TestExecutorModule());

        Supplier<Torrent> torrentSupplier = new CachingTorrentSupplier(() -> new MetadataService().fromUrl(METAINFO_URL));
        builder.torrentSupplier(torrentSupplier);
        return builder;
    }

    private String getTestName() {
        return getClass().getName();
    }

    private static TorrentFiles getSingleFile() {
        return new TorrentFiles(Collections.singletonMap(new String[]{FILE_NAME}, SINGLE_FILE_CONTENT));
    }

    /**
     * Loads torrent only once.
     */
    private static class CachingTorrentSupplier implements Supplier<Torrent> {

        private final Supplier<Torrent> delegate;
        private volatile Torrent torrent;
        private final Object lock;

        public CachingTorrentSupplier(Supplier<Torrent> delegate) {
            this.delegate = delegate;
            this.lock = new Object();
        }

        @Override
        public Torrent get() {
            if (torrent == null) {
                synchronized (lock) {
                    if (torrent == null) {
                        torrent = delegate.get();
                    }
                }
            }
            return torrent;
        }
    }
}
