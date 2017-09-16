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

package bt.bencoding.model;

import bt.bencoding.BEEncoder;
import bt.bencoding.BEType;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

/**
 * BEncoded dictionary.
 *
 * @since 1.0
 */
public class BEMap implements BEObject<Map<String, BEObject<?>>> {

    private byte[] content;
    private Map<String, BEObject<?>> value;
    private BEEncoder encoder;

    /**
     * @param content Binary representation of this dictionary, as read from source.
     * @param value Parsed value
     * @since 1.0
     */
    public BEMap(byte[] content, Map<String, BEObject<?>> value) {
        this.content = content;
        this.value = Collections.unmodifiableMap(value);
        encoder = BEEncoder.encoder();
    }

    @Override
    public BEType getType() {
        return BEType.MAP;
    }

    @Override
    public byte[] getContent() {
        return content;
    }

    @Override
    public Map<String, BEObject<?>> getValue() {
        return value;
    }

    @Override
    public void writeTo(OutputStream out) throws IOException {
        encoder.encode(this, out);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public boolean equals(Object obj) {

        if (obj == null || !(obj instanceof BEMap)) {
            return false;
        }

        if (obj == this) {
            return true;
        }

        return value.equals(((BEMap) obj).getValue());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
