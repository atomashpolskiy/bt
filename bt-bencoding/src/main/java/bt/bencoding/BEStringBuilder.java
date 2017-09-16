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

import bt.bencoding.model.BEString;

import java.io.ByteArrayOutputStream;

class BEStringBuilder implements BEObjectBuilder<BEString> {

    static final char DELIMITER = ':';

    private ByteArrayOutputStream buf;
    private int length;
    private int bytesAcceptedCount;
    private boolean shouldReadBody;

    BEStringBuilder() {
        buf = new ByteArrayOutputStream();
    }

    @Override
    public boolean accept(int b) {

        char c = (char) b;
        if (shouldReadBody) {
            if (bytesAcceptedCount + 1 > length) {
                return false;
            }
        } else {
            if (bytesAcceptedCount == 0 && !Character.isDigit(c)) {
                throw new IllegalArgumentException(
                        "Unexpected token while reading string's length (as ASCII char): " + c);
            }
            if (c == DELIMITER) {
                shouldReadBody = true;
                bytesAcceptedCount = 0;
                length = Integer.parseInt(buf.toString());
                buf = new ByteArrayOutputStream(length);
                return true;
            }
        }

        buf.write(b);
        bytesAcceptedCount++;
        return true;
    }

    @Override
    public BEString build() {
        if (!shouldReadBody) {
            throw new IllegalStateException("Can't build string: no content");
        }
        if (bytesAcceptedCount < length) {
            throw new IllegalStateException("Can't build string: insufficient content");
        }
        return new BEString(buf.toByteArray());
    }

    @Override
    public BEType getType() {
        return BEType.STRING;
    }
}
