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
