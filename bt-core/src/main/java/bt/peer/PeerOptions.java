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

/**
 * Provides information on various options, preferences and modes of operations of a particular peer.
 * Instances of this class are immutable; mutator methods return new instances
 * with corresponding changes applied to the original object.
 *
 * @since 1.2
 */
public class PeerOptions {

    public static final PeerOptions DEFAULT_OPTS = buildPeerOptions((byte) 0);

    /**
     * Returns default options.
     *
     * @return Options with all values set to their defaults.
     * @since 1.2
     */
    public static PeerOptions defaultOptions() {
        return DEFAULT_OPTS;
    }

    private static final int PREFERS_ENCRYPTION = 0x1;
    private static final int IS_SEED = 0x2;
    private static final int SUPPORT_uTP = 0x4;
    private static final int UT_HOLE_PUNCH = 0x8;
    private static final int OUTGOING_CONNECTION = 0x10;


    private static PeerOptions buildPeerOptions(boolean prefersEncryption,
                                                boolean isSeed,
                                                boolean uTPsupport,
                                                boolean utHolePunch,
                                                boolean outgoingConnection) {
        byte bitField = 0;
        if (prefersEncryption) bitField |= PREFERS_ENCRYPTION;
        if (isSeed) bitField |= IS_SEED;
        if (uTPsupport) bitField |= SUPPORT_uTP;
        if (utHolePunch) bitField |= UT_HOLE_PUNCH;
        if (outgoingConnection) bitField |= OUTGOING_CONNECTION;
        return new PeerOptions(bitField);
    }

    public static PeerOptions buildPeerOptions(byte bitField) {
        return new PeerOptions(bitField);
    }

    private final byte bitField;

    private PeerOptions(byte bitField) {
        this.bitField = bitField;
    }

    public boolean prefersEncryption() {
        return getField(PREFERS_ENCRYPTION);
    }

    public boolean isSeed() {
        return getField(IS_SEED);
    }

    public boolean utpSupport() {
        return getField(SUPPORT_uTP);
    }

    public boolean utHolePunch() {
        return getField(UT_HOLE_PUNCH);
    }

    public boolean outgoingConnection() {
        return getField(OUTGOING_CONNECTION);
    }

    private boolean getField(int bitMask) {
        return (bitField & bitMask) != 0;
    }

    public byte getPExBitField() {
        return bitField;
    }

    /**
     * @return Copy of the original options with adjusted Message Stream Encryption policy
     * @since 1.2
     */
    public PeerOptions prefersEncryption(boolean prefersEncryption) {
        return new Builder().prefersEncryption(prefersEncryption).build();
    }

    /**
     * Return a new builder for PeerOptions
     *
     * @return the builder for the peer builder options
     * @since 1.10
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @since 1.2
     */
    public static class Builder {
        private boolean prefersEncryption = false;
        private boolean isSeed = false;
        private boolean uTPsupport = false;
        private boolean utHolePunch = false;
        private boolean outgoingConnection = false;

        private Builder() {
        }

        /**
         * Indicate preference regarding Message Stream Encryption.
         *
         * @since 1.10
         */
        public Builder prefersEncryption(boolean prefersEncryption) {
            this.prefersEncryption = prefersEncryption;
            return this;
        }

        /**
         * Indicate policy regarding Message Stream Encryption.
         *
         * @since 1.10
         */
        public Builder outgoingConnection(boolean outgoingConnection) {
            this.outgoingConnection = outgoingConnection;
            return this;
        }

        /**
         * @since 1.10
         */
        public PeerOptions build() {
            return PeerOptions.buildPeerOptions(prefersEncryption, isSeed, uTPsupport, utHolePunch, outgoingConnection);
        }
    }
}
