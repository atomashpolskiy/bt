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
