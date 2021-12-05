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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * BEncoded list. May contain objects of different types.
 *
 * @since 1.0
 */
public class BEList implements BEObject<List<? extends BEObject<?>>> {
    private static final BEEncoder ENCODER = BEEncoder.encoder();
    private final byte[] content;
    private final List<? extends BEObject<?>> value;

    public BEList(List<? extends BEObject<?>> value) {
        this(null, value);
    }

    /**
     * @param content Binary representation of this list, as read from source.
     * @param value   Parsed value
     * @since 1.0
     */
    public BEList(byte[] content, List<? extends BEObject<?>> value) {
        this.content = content;
        this.value = Collections.unmodifiableList(value);
    }

    @Override
    public BEType getType() {
        return BEType.LIST;
    }

    @Override
    public byte[] getContent() {
        return content;
    }

    @Override
    public List<? extends BEObject<?>> getValue() {
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

        if (obj == null || !(obj instanceof BEList)) {
            return false;
        }

        if (obj == this) {
            return true;
        }

        return value.equals(((BEList) obj).getValue());
    }

    @Override
    public String toString() {
        return Arrays.toString(value.toArray());
    }
}
