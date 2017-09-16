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

package bt.torrent;

import bt.data.ChunkDescriptor;
import bt.data.DataRange;
import org.junit.BeforeClass;

import java.util.Arrays;

public abstract class BaseBitfieldTest {

    protected static long blockSize = 4;

    protected static ChunkDescriptor emptyChunk, completeChunk;

    @BeforeClass
    public static void setUp() {
        emptyChunk = mockChunk(new byte[]{0,0,0,0});
        completeChunk = mockChunk(new byte[]{1,1,1,1});
    }

    protected static ChunkDescriptor mockChunk(byte[] bitfield) {

        byte[] _bitfield = Arrays.copyOf(bitfield, bitfield.length);

        return new ChunkDescriptor() {

            @Override
            public byte[] getChecksum() {
                return new byte[0];
            }

            @Override
            public int blockCount() {
                return _bitfield.length;
            }

            @Override
            public long length() {
                return blockSize * blockCount();
            }

            @Override
            public long blockSize() {
                return blockSize;
            }

            @Override
            public long lastBlockSize() {
                return length() % blockSize();
            }

            @Override
            public boolean isPresent(int blockIndex) {
                return _bitfield[blockIndex] == 1;
            }

            @Override
            public boolean isComplete() {
                for (byte b : _bitfield) {
                    if (b != 1) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public boolean isEmpty() {
                for (byte b : _bitfield) {
                    if (b == 1) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public DataRange getData() {
                throw new UnsupportedOperationException();
            }
        };
    }
}
