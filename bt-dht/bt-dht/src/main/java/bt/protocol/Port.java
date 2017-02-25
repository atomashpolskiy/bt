package bt.protocol;

import bt.protocol.handler.PortMessageHandler;

/**
 * @since 1.1
 */
public final class Port implements Message {

    private int port;

    /**
     * @param port Port, on which the DHT service is listening on
     * @throws InvalidMessageException If port number is out of the allowed range (0-65535)
     * @since 1.1
     */
    public Port(int port) throws InvalidMessageException {

        if (port < 0 || port > 65535) {
            throw new InvalidMessageException("Invalid argument: port (" + port + ")");
        }

        this.port = port;
    }

    /**
     * @since 1.1
     */
    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "] port {" + port + "}";
    }

    @Override
    public Integer getMessageId() {
        return PortMessageHandler.PORT_ID;
    }
}
