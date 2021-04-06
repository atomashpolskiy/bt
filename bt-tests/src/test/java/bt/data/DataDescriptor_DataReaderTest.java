/*
 * Copyright (c) 2016—2019 Andrei Tomashpolskiy and individual contributors.
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

import bt.TestUtil;
import bt.data.digest.Digester;
import bt.data.digest.SHA1Digester;
import bt.event.EventBus;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;
import bt.service.CryptoUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static bt.data.ChunkDescriptorTestUtil.mockTorrent;
import static bt.data.ChunkDescriptorTestUtil.mockTorrentFile;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

public class DataDescriptor_DataReaderTest {

    private static long chunkSize = 29; // prime
    private static int chunkCount = 256;
    private static long fileSize = chunkSize * chunkCount;
    private static byte[] FILE = TestUtil.sequence((int) fileSize);

    @Rule
    public TestFileSystemStorage storage = new TestFileSystemStorage();

    private ChunkVerifier verifier;
    private EventBus eventBus;
    private TorrentId torrentId;
    private DataDescriptor descriptor;

    @Before
    public void before() {
        int step = 8;
        Digester digester = SHA1Digester.rolling(step);
        int numOfHashingThreads = 4;
        int transferBlockSize = 4;

        this.verifier = new DefaultChunkVerifier(digester, numOfHashingThreads, true);
        this.eventBus = new EventBus();

        byte[][] chunkHashes = new byte[chunkCount][];
        for (int i = 0; i < chunkCount; i++) {
            int from = (int) (i * chunkSize);
            int to = (int) (from + chunkSize);
            chunkHashes[i] = CryptoUtil.getSha1Digest(Arrays.copyOfRange(FILE, from, to));
        }

        Torrent torrent = mockTorrent("1.bin", fileSize, chunkSize, chunkHashes,
                mockTorrentFile(fileSize, "1.bin"));
        this.torrentId = torrent.getTorrentId();

        IDataDescriptorFactory dataDescriptorFactory = new DataDescriptorFactory(
                new DataReaderFactory(eventBus), verifier, transferBlockSize);
        this.descriptor = dataDescriptorFactory.createDescriptor(torrent, storage);
    }

    @Test
    public void test() throws InterruptedException {
        int p = chunkCount / 3;
        for (int i = 0; i < p; i++) {
            ChunkDescriptor chunkDescriptor = descriptor.getChunkDescriptors().get(i);
            int from = (int) (i * chunkSize);
            int to = (int) (from + chunkSize);
            byte[] bytes = Arrays.copyOfRange(FILE, from, to);
            chunkDescriptor.getData().putBytes(bytes);
            assertTrue(verifier.verify(chunkDescriptor));
            descriptor.getBitfield().markVerified(i);
        }

        ReadableByteChannel channel = descriptor.getReader().createChannel();
        ByteBuffer data = ByteBuffer.allocate(FILE.length);
        AtomicBoolean finished = new AtomicBoolean(false);
        Object condition = new Object();
        new Thread(() -> {
            ByteBuffer buffer = ByteBuffer.allocate(7); // prime
            try {
                int read;
                while ((read = channel.read(buffer)) >= 0) {
                    if (read > 0) {
                        buffer.flip();
                        data.put(buffer);
                        buffer.clear();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                finished.set(true);
                synchronized (condition) {
                    condition.notifyAll();
                }
            }
        }).start();

        List<Integer> remainingPieces = IntStream.range(p, chunkCount).boxed().collect(Collectors.toList());
        Collections.shuffle(remainingPieces);
        for (int pieceIndex : remainingPieces) {
            ChunkDescriptor chunkDescriptor = descriptor.getChunkDescriptors().get(pieceIndex);
            int from = (int) (pieceIndex * chunkSize);
            int to = (int) (from + chunkSize);
            byte[] bytes = Arrays.copyOfRange(FILE, from, to);
            chunkDescriptor.getData().putBytes(bytes);
            assertTrue(verifier.verify(chunkDescriptor));
            descriptor.getBitfield().markVerified(pieceIndex);
            eventBus.firePieceVerified(torrentId, pieceIndex);

            Thread.sleep(ThreadLocalRandom.current().nextInt(10));
        }

        synchronized (condition) {
            while (!finished.get()) {
                condition.wait();
            }
        }

        data.flip();
        assertArrayEquals(FILE, data.array());
    }
}
