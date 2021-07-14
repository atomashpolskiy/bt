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

package bt.bencoding.types;

import bt.bencoding.model.BEObject;
import bt.bencoding.serializers.BEEncoder;
import bt.bencoding.BEType;
import com.google.common.base.Suppliers;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Supplier;

/**
 * BEncoded string.
 *
 * @since 1.0
 */
public class BEString implements BEObject<byte[]> {
    private static final BEEncoder ENCODER = BEEncoder.encoder();
    private final byte[] content;

    private final Supplier<String> stringValue;

    /**
     * @param content Binary representation of this string, as read from source.
     *                It is also the value of this string, being a UTF-8 encoded byte array.
     * @since 1.0
     */
    public BEString(byte[] content) {
        this.content = content;
        stringValue = Suppliers.memoize(() -> new String(content, StandardCharsets.UTF_8));
    }

    public BEString(String content) {
        this.content = content.getBytes(StandardCharsets.UTF_8);
        this.stringValue = () -> content;
    }

    @Override
    public BEType getType() {
        return BEType.STRING;
    }

    @Override
    public byte[] getContent() {
        return content;
    }

    @Override
    public byte[] getValue() {
        return content;
    }

    @Override
    public void writeTo(OutputStream out) throws IOException {
        ENCODER.encode(this, out);
    }

    public String getValueAsString() {
        return new String(content, StandardCharsets.UTF_8);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(content);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof BEString)) {
            return false;
        }

        if (obj == this) {
            return true;
        }

        return Arrays.equals(content, ((BEString) obj).getContent());
    }

    @Override
    public String toString() {
        return stringValue.get();
    }
}
