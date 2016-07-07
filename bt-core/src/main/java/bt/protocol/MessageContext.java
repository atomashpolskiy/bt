package bt.protocol;

import bt.net.Peer;

public class MessageContext {

    private Peer peer;
    private Message message;

    public MessageContext(Peer peer) {
        this.peer = peer;
    }

    public Peer getPeer() {
        return peer;
    }

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }
}
