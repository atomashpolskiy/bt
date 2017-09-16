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
import java.math.BigInteger;

/**
 * BEncoded integer.
 *
 * <p>«BEP-3: The BitTorrent Protocol Specification» defines integers
 * as unsigned numeric values with an arbitrary number of digits.
 *
 * @since 1.0
 */
public class BEInteger implements BEObject<BigInteger> {

    private byte[] content;
    private BigInteger value;
    private BEEncoder encoder;

    /**
     * @param content Binary representation of this integer, as read from source.
     * @param value Parsed value
     * @since 1.0
     */
    public BEInteger(byte[] content, BigInteger value) {
        this.content = content;
        this.value = value;
        encoder = BEEncoder.encoder();
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
    public BigInteger getValue() {
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

        if (obj == null || !(obj instanceof BEInteger)) {
            return false;
        }

        if (obj == this) {
            return true;
        }

        return value.equals(((BEInteger) obj).getValue());
    }

    @Override
    public String toString() {
        return value.toString(10);
    }
}
