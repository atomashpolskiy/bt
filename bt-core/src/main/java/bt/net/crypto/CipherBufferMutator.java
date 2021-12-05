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

package bt.net.crypto;

import bt.data.digest.SoftThreadLocal;
import bt.net.buffer.BufferMutator;

import javax.crypto.Cipher;
import java.nio.ByteBuffer;

/**
 * Mutator, that performs encryption or decryption of data.
 *
 * @since 1.6
 */
public class CipherBufferMutator implements BufferMutator {

    private final Cipher cipher;
    private SoftThreadLocal<byte[]> bytesTL = new SoftThreadLocal<>(() -> new byte[16 * 1024]);

    /**
     * @since 1.6
     */
    public CipherBufferMutator(Cipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public void mutate(ByteBuffer buffer) {
        if (buffer.hasRemaining()) {
            int position = buffer.position();
            byte[] bytes = bytesTL.getValue();
            int remaining = buffer.remaining();
            if (bytes.length < remaining) {
                bytes = new byte[remaining];
                bytesTL.setValue(bytes);
            }

            buffer.get(bytes, 0, remaining);
            buffer.position(position);
            try {
                int l = cipher.update(bytes, 0, remaining, bytes);
                buffer.put(bytes, 0, l);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
