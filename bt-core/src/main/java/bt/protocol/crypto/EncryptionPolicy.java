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

package bt.protocol.crypto;

/**
 * Message Stream Encryption policy
 *
 * @since 1.2
 */
public enum EncryptionPolicy {

    /**
     * @since 1.2
     */
    REQUIRE_PLAINTEXT,

    /**
     * @since 1.2
     */
    PREFER_PLAINTEXT,

    /**
     * @since 1.2
     */
    PREFER_ENCRYPTED,

    /**
     * @since 1.2
     */
    REQUIRE_ENCRYPTED;

    public boolean isCompatible(EncryptionPolicy that) {
        if (this == REQUIRE_PLAINTEXT && that == REQUIRE_ENCRYPTED) {
            return false;
        } else if (this == REQUIRE_ENCRYPTED && that == REQUIRE_PLAINTEXT) {
            return false;
        }
        return true;
    }
}
