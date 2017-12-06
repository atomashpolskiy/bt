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

package bt.data;

import bt.data.file.FileSystemStorage;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;
import org.junit.rules.ExternalResource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;

// TODO: replace this with in-memory storage
class TestFileSystemStorage extends ExternalResource implements Storage {

    private static final String ROOT_PATH = "target/rt";

    private File rootDirectory;
    private volatile Storage delegate;
    private final Object lock;

    private Collection<StorageUnit> units;

    public TestFileSystemStorage() {
        rootDirectory = new File(ROOT_PATH);
        if (!rootDirectory.mkdirs()) {
            throw new RuntimeException("Failed to create directories: " + rootDirectory);
        }
        units = new ArrayList<>();
        lock = new Object();
    }

    @Override
    public StorageUnit getUnit(Torrent torrent, TorrentFile torrentFile) {
        StorageUnit unit = getDelegate().getUnit(torrent, torrentFile);
        units.add(unit);
        return unit;
    }

    private Storage getDelegate() {
        if (delegate == null) {
            synchronized (lock) {
                if (delegate == null) {
                    delegate = new FileSystemStorage(rootDirectory);
                }
            }
        }
        return delegate;
    }

    public File getRoot() {
        return rootDirectory;
    }

    @Override
    protected void after() {
        try {
            for (StorageUnit unit : units) {
                unit.close();
            }
            deleteRecursive(rootDirectory);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void deleteRecursive(File file) throws IOException {
        if (!file.exists()) {
            return;
        }

        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }

        Files.delete(file.toPath());
    }
}
