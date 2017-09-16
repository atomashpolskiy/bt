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

package bt.peer;

import bt.protocol.crypto.EncryptionPolicy;

/**
 * Provides information on various options, preferences and modes of operations of a particular peer.
 * Instances of this class are immutable; mutator methods return new instances
 * with corresponding changes applied to the original object.
 *
 * @since 1.2
 */
public class PeerOptions {

    /**
     * Returns default options.
     *
     * @return Options with all values set to their defaults.
     * @since 1.2
     */
    public static PeerOptions defaultOptions() {
        return new PeerOptions(EncryptionPolicy.PREFER_PLAINTEXT);
    }

    private final EncryptionPolicy encryptionPolicy;

    private PeerOptions(EncryptionPolicy encryptionPolicy) {
        this.encryptionPolicy = encryptionPolicy;
    }

    /**
     * @return Message Stream Encryption policy
     * @since 1.2
     */
    public EncryptionPolicy getEncryptionPolicy() {
        return encryptionPolicy;
    }

    /**
     * @return Copy of the original options with adjusted Message Stream Encryption policy
     * @since 1.2
     */
    public PeerOptions withEncryptionPolicy(EncryptionPolicy encryptionPolicy) {
        return new Builder().encryptionPolicy(encryptionPolicy).build();
    }

    /**
     * @since 1.2
     */
    private static class Builder {
        private EncryptionPolicy encryptionPolicy;

        private Builder() {
        }

        /**
         * Indicate policy regarding Message Stream Encryption.
         *
         * @since 1.2
         */
        public Builder encryptionPolicy(EncryptionPolicy encryptionPolicy) {
            this.encryptionPolicy = encryptionPolicy;
            return this;
        }

        /**
         * @since 1.2
         */
        public PeerOptions build() {
            return new PeerOptions(encryptionPolicy);
        }
    }
}
