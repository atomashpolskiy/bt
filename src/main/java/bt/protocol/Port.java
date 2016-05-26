package bt.protocol;

public class Port implements Message {

    private int port;

    Port(int port) {
        this.port = port;
    }

    @Override
    public MessageType getType() {
        return MessageType.PORT;
    }

    public int getPort() {
        return port;
    }
}
