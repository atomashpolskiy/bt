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
import java.math.BigInteger;
import java.util.Objects;

/**
 * BEncoded integer.
 *
 * <p>«BEP-3: The BitTorrent Protocol Specification» defines integers
 * as unsigned numeric values with an arbitrary number of digits.
 *
 * @since 1.0
 */
public class BEInteger implements BEObject<Number> {
    private static final BEEncoder ENCODER = BEEncoder.encoder();
    private final byte[] content;
    private final Number value;

    public BEInteger(Number value) {
        this(null, value);
    }

    /**
     * @param content Binary representation of this integer, as read from source.
     * @param value   Parsed value
     * @since 1.0
     */
    public BEInteger(byte[] content, Number value) {
        this.content = content;
        this.value = Objects.requireNonNull(value);
    }

    @Override
    public BEType getType() {
        return BEType.INTEGER;
    }

    @Override
    public byte[] getContent() {
        return content;
    }

    @Override
    public Number getValue() {
        return value;
    }

    public long longValueExact() {
        if (value instanceof Integer || value instanceof Long) {
            return value.longValue();
        }
        if (value instanceof BigInteger) {
            return ((BigInteger) value).longValueExact();
        }
        throw new IllegalStateException("unknown type for value: " + (value == null ? "null" : value.getClass()));
    }

    @Override
    public void writeTo(OutputStream out) throws IOException {
        ENCODER.encode(this, out);
    }

    @Override
    public int hashCode() {
        // for integers greater than Long.MAX_VALUE, collisions may occur. However, this shouldn't be a use case.
        return Long.hashCode(value.longValue());
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof BEInteger)) {
            return false;
        }

        if (obj == this) {
            return true;
        }

        if (isLongOrInteger() && ((BEInteger) obj).isLongOrInteger()) {
            return longValueExact() == ((BEInteger) obj).longValueExact();
        }

        return value.toString().equals(((BEInteger) obj).getValue().toString());
    }

    private boolean isLongOrInteger() {
        return value instanceof Long || value instanceof Integer;
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
