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

import java.io.ByteArrayOutputStream;

abstract class BEPrefixedTypeBuilder<T extends BEObject> implements BEObjectBuilder<T> {

    private ByteArrayOutputStream buf;
    private boolean receivedPrefix;
    private boolean receivedEOF;

    BEPrefixedTypeBuilder() {
        buf = new ByteArrayOutputStream();
    }

    @Override
    public final boolean accept(int b) {
        return accept(b, true);
    }

    // work-around for duplicate logging of received bytes
    // when BEPrefixTypeBuilder.accept(int) is called by itself
    // -- descendants should use this method instead
    protected boolean accept(int b, boolean shouldLog) {

        if (receivedEOF) {
            return false;
        }

        if (shouldLog) {
            buf.write(b);
        }

        if (!receivedPrefix) {
            BEType type = getType();
            if (b == BEParser.getPrefixForType(type)) {
                receivedPrefix = true;
                return true;
            } else {
                throw new IllegalArgumentException("Invalid prefix for type " + type.name().toLowerCase()
                        + " (as ASCII char): " + (char) b);
            }
        }

        if (b == BEParser.EOF && acceptEOF()) {
            receivedEOF = true;
            return true;
        }

        return doAccept(b);
    }

    @Override
    public T build() {

        if (!receivedPrefix) {
            throw new IllegalStateException("Can't build " + getType().name().toLowerCase() + " -- no content");
        }
        if (!receivedEOF) {
            throw new IllegalStateException("Can't build " + getType().name().toLowerCase() + " -- content was not terminated");
        }
        return doBuild(buf.toByteArray());
    }

    protected abstract boolean doAccept(int b);
    protected abstract T doBuild(byte[] content);
    protected abstract boolean acceptEOF();
}
