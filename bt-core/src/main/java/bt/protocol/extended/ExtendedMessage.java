package bt.protocol.extended;

import bt.protocol.Message;

public class ExtendedMessage implements Message {

    @Override
    public Integer getMessageId() {
        return ExtendedProtocol.EXTENDED_MESSAGE_ID;
    }
}
