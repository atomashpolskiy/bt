package bt.tracker.udp;

import bt.protocol.Protocols;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Random;

abstract class UdpTrackerMessage {

    private int messageType;
    private int id;

    public UdpTrackerMessage(int messageType) {
        this.messageType = messageType;
        this.id = new Random(System.currentTimeMillis()).nextInt();
    }

    public int getId() {
        return id;
    }

    public int getMessageType() {
        return messageType;
    }

    public void writeTo(OutputStream out) throws IOException {
        out.write(Protocols.getIntBytes(messageType));
        out.write(Protocols.getIntBytes(id));
    }

    protected abstract void writeBodyTo(OutputStream out) throws IOException;
}
