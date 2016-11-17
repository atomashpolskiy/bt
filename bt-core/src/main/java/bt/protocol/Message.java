package bt.protocol;

/**
 * Supertype of all BitTorrent messages.
 *
 * @since 1.0
 */
public interface Message {

    /**
     * @return Unique message ID, as defined by the standard BitTorrent protocol.
     * @since 1.0
     */
    Integer getMessageId();
}
