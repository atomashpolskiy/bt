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
    REQUIRE_ENCRYPTED
}
