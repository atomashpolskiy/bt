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

package bt.bencoding.serializers;

import bt.bencoding.BEType;
import bt.bencoding.types.BEString;

class BEStringDecoder implements BEObjectDecoder<BEString> {
    static final char DELIMITER = ':';

    private byte[] buf;
    private long length;
    private int bytesAcceptedCount;
    private boolean shouldReadBody;

    BEStringDecoder() {
    }

    @Override
    public boolean accept(int b) {
        if (shouldReadBody) {
            if (bytesAcceptedCount + 1 > length) {
                return false;
            }
            buf[bytesAcceptedCount] = (byte) b;
        } else {
            if (b == DELIMITER) {
                if (bytesAcceptedCount == 0) {
                    throw new IllegalArgumentException(
                            "Unexpected delimiter found before string's length (as ASCII char): " + (char) b);
                }

                shouldReadBody = true;
                bytesAcceptedCount = 0;
                buf = new byte[(int) length];
                return true;
            } else {
                if (!BEParser.IS_DIGIT.matches((char) b) || (bytesAcceptedCount > 0 && length == 0)) {
                    throw new IllegalArgumentException(
                            "Unexpected token while reading string's length (as ASCII char): " + (char) b);
                }
                length = 10 * length + (b - '0');
                if (length > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException(
                            "Cannot read string longer than " + Integer.MAX_VALUE + " characters.");
                }
            }
        }

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
        return new BEString(buf);
    }

    @Override
    public BEType getType() {
        return BEType.STRING;
    }
}
