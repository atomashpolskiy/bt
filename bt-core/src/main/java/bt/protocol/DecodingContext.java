package bt.protocol;

import bt.net.Peer;

/**
 * Instances of this class contain all necessary information
 * for a message handler to decode a peer's message,
 * and also act as a means to carry message
 * throughout the decoding chain.
 *
 * @since 1.0
 */
public class DecodingContext {

    private Peer peer;
    private Message message;

    /**
     * Create a decoding context for a particular peer.
     *
     * @since 1.0
     */
    public DecodingContext(Peer peer) {
        this.peer = peer;
    }

    /**
     * @since 1.0
     */
    public Peer getPeer() {
        return peer;
    }

    /**
     * @since 1.0
     */
    public Message getMessage() {
        return message;
    }

    /**
     * @since 1.0
     */
    public void setMessage(Message message) {
        this.message = message;
    }
}
