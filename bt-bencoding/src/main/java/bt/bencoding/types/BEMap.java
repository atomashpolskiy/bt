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

package bt.bencoding.types;

import bt.bencoding.BEType;
import bt.bencoding.model.BEObject;
import bt.bencoding.serializers.BEEncoder;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * BEncoded dictionary.
 *
 * @since 1.0
 */
public class BEMap implements BEObject<Map<String, BEObject<?>>> {
    private static final BEEncoder ENCODER = BEEncoder.encoder();
    private final byte[] content;
    private final Map<String, BEObject<?>> value;

    public BEMap(Map<String, BEObject<?>> value) {
        this(null, value);
    }

    /**
     * @param content Binary representation of this dictionary, as read from source.
     * @param value   Parsed value
     * @since 1.0
     */
    public BEMap(byte[] content, Map<String, BEObject<?>> value) {
        this.content = content;
        this.value = Collections.unmodifiableMap(Objects.requireNonNull(value));
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
        ENCODER.encode(this, out);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public boolean equals(Object obj) {

        if (!(obj instanceof BEMap)) {
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
