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

package bt.data.digest;

import bt.BtException;
import bt.data.DataRange;
import bt.data.range.Range;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class JavaSecurityDigester implements Digester {

    private final String algorithm;
    private final int step;
    private final SoftThreadLocal<byte[]> bufferTL;
    private final SoftThreadLocal<MessageDigest> digestTL = new SoftThreadLocal<>(() -> createDigest(), messageDigest -> {
        messageDigest.reset();
        return messageDigest;
    });

    public JavaSecurityDigester(String algorithm, int step) {
        try {
            // verify that implementation for the algorithm exists
            MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("No such algorithm: " + algorithm, e);
        }
        this.algorithm = algorithm;
        this.step = step;
        bufferTL = new SoftThreadLocal<>(() -> new byte[step]);
    }

    public String getAlgorithm() {
        return algorithm;
    }

    @Override
    public byte[] digest(DataRange data) {
        MessageDigest digest = digestTL.getValue();

        data.visitUnits((unit, off, lim) -> {
            long remaining = lim - off;
            if (remaining > Integer.MAX_VALUE) {
                throw new BtException("Too much data -- can't read to buffer");
            }
            byte[] bytes = bufferTL.getValue();
            do {
                ByteBuffer wrap = ByteBuffer.wrap(bytes);
                if (remaining < step) {
                    wrap.limit((int) remaining);
                }
                int read = unit.readBlock(wrap, off);
                if (read == -1) {
                    // end of data, terminate
                    return false;
                }
                digest.update(bytes, 0, read);
                remaining -= read;
                off += read;
            } while (remaining > 0);

            return true;
        });

        return digest.digest();
    }

    @Override
    public byte[] digestForced(DataRange data) {
        MessageDigest digest = digestTL.getValue();

        data.visitUnits((unit, off, lim) -> {
            long remaining = lim - off;
            if (remaining > Integer.MAX_VALUE) {
                throw new BtException("Too much data -- can't read to buffer");
            }
            byte[] bytes = bufferTL.getValue();
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            do {
                if (remaining < step) {
                    buffer.limit((int) remaining);
                }
                unit.readBlockFully(buffer, off);
                if (buffer.hasRemaining()) {
                    throw new IllegalStateException("Failed to read data fully: " + buffer.remaining() + " bytes remaining");
                }
                digest.update(bytes,0, buffer.flip().limit());
                remaining -= step;
                off += step;
            } while (remaining > 0);

            return true;
        });

        return digest.digest();
    }

    @Override
    public byte[] digest(Range<?> data) {
        MessageDigest digest = digestTL.getValue();

        long len = data.length();
        if (len <= step) {
            digest.update(data.getBytes());
        } else {
            for (long i = 0; i < len; i += step) {
                digest.update(data.getSubrange(i, Math.min((len - i), step)).getBytes());
            }
        }
        return digest.digest();
    }

    private MessageDigest createDigest() {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            // not going to happen
            throw new BtException("Unexpected error", e);
        }
    }
}
