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

/**
 * BEncoded document parse exception.
 *
 * @since 1.0
 */
public class BtParseException extends RuntimeException {

    private byte[] scannedContents;

    /**
     * @since 1.0
     */
    public BtParseException(String message, byte[] scannedContents, Throwable cause) {
        super(message, cause);
        this.scannedContents = scannedContents;
    }

    /**
     * @since 1.0
     */
    public BtParseException(String message, byte[] scannedContents) {
        super(message);
        this.scannedContents = scannedContents;
    }

    /**
     * Get the scanned portion of the source document.
     *
     * @return Scanned portion of the source document
     * @since 1.0
     */
    public byte[] getScannedContents() {
        return scannedContents;
    }
}
