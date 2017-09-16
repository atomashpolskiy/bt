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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

class TorrentFiles {

    private Map<String[], byte[]> files;

    TorrentFiles(Map<String[], byte[]> files) {
        this.files = files;
    }

    public void createFiles(Path root) {
        createRoot(root);
        files.forEach((path, content) -> {
            writeContents.accept(resolve(root, path), content);
        });
    }

    private void createRoot(Path root) {
        if (Files.exists(root)) {
            if (!Files.isDirectory(root)) {
                throw new IllegalStateException("File already exists and is not a directory: " + root);
            } else {
                long fileCount;
                try {
                    fileCount = Files.list(root).count();
                } catch (IOException e) {
                    throw new RuntimeException("Failed to create directory: " + root, e);
                }
                if (fileCount > 0) {
                    throw new RuntimeException("Directory is not empty: " + root);
                }
            }
        } else {
            try {
                 Files.createDirectories(root);
            } catch (IOException e) {
                throw new RuntimeException("Failed to create directory: " + root, e);
            }
        } // else root exists and is a directory
    }

    private Path resolve(Path root, String[] path) {
        // should we forbid empty paths?
        Path file = root;
        for (String element : path) {
            file = file.resolve(element);
        }
        return file;
    }

    public boolean verifyFiles(Path root) {
        for (Map.Entry<String[], byte[]> entry : files.entrySet()) {
            Path file = resolve(root, entry.getKey());
            byte[] expectedContent = entry.getValue();

            if (!verifyContents.apply(file, expectedContent)) {
                return false;
            }
        }
        return true;
    }

    private BiConsumer<Path, byte[]> writeContents = (file, content) -> {
        try {
            ByteBuffer buf = ByteBuffer.wrap(content);
            try (ByteChannel fout = Files.newByteChannel(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                int written = 1;
                while (buf.hasRemaining() && written > 0) {
                  written = fout.write(buf);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create file: " + file, e);
        }
    };

    private BiFunction<Path, byte[], Boolean> verifyContents = (file, expectedContent) -> {
        if (!Files.exists(file)) {
            return false;
        }

        try {
            ByteBuffer buf = ByteBuffer.allocate((int) Files.size(file));
            try (ByteChannel fin = Files.newByteChannel(file, StandardOpenOption.READ)) {
                int read = 1;
                while (buf.hasRemaining() && read > 0) {
                  read = fin.read(buf);
                }
            }
            return Arrays.equals(expectedContent, buf.array());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + file, e);
        }
    };
}
