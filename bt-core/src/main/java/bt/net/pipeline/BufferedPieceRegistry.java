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

package bt.net.pipeline;

import bt.net.buffer.BufferedData;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class BufferedPieceRegistry implements IBufferedPieceRegistry {

    private final ConcurrentMap<Long, BufferedData> bufferMap;

    public BufferedPieceRegistry() {
        this.bufferMap = new ConcurrentHashMap<>();
        // TODO: Daemon cleaner
    }

    @Override
    public boolean addBufferedPiece(int pieceIndex, int offset, BufferedData buffer) {
        if (pieceIndex < 0) {
            throw new IllegalArgumentException("Illegal piece index: " + pieceIndex);
        }
        Objects.requireNonNull(buffer);

        BufferedData existing = bufferMap.putIfAbsent(zip(pieceIndex, offset), buffer);
        return (existing == null);
    }

    @Override
    public BufferedData getBufferedPiece(int pieceIndex, int offset) {
        return bufferMap.remove(zip(pieceIndex, offset));
    }

    private static long zip(int pieceIndex, int offset) {
        return (((long)pieceIndex) << 32) + offset;
    }
}
