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

package bt.bencoding;

import bt.bencoding.model.BEObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;

class Scanner {

    private final MemoizingPushbackInputStream source;

    Scanner(byte[] bs) {
        this(new ByteArrayInputStream(bs));
    }

    Scanner(InputStream in) {
        source = new MemoizingPushbackInputStream(in);
    }

    <T extends BEObject> T readObject(BEObjectBuilder<T> builder) throws Exception {

        source.resetContents();

        int c;
        while ((c = source.read()) != -1 && builder.accept(c))
            ;
        return builder.build();
    }

    int peek() {
        return source.peek();
    }

    byte[] getScannedContents() {
        return source.getContents();
    }

    void close() {
        try {
            source.close();
        } catch (IOException e) {
            // do nothing
        }
    }

    private static class MemoizingPushbackInputStream extends InputStream {

        private PushbackInputStream delegate;
        private ByteArrayOutputStream buf;

        MemoizingPushbackInputStream(InputStream delegate) {
            this.delegate = new PushbackInputStream(delegate);
            buf = new ByteArrayOutputStream();
        }

        @Override
        public int read() throws IOException {
            int c = delegate.read();
            buf.write(c);
            return c;
        }

        public int peek() {
            try {
                int c = delegate.read();
                delegate.unread(c);
                return c;
            } catch (IOException e) {
                throw new RuntimeException("Unexpected I/O exception", e);
            }
        }

        public void resetContents() {
            buf = new ByteArrayOutputStream();
        }

        public byte[] getContents() {
            return buf.toByteArray();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
