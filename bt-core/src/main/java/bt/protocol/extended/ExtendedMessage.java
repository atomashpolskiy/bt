package bt.protocol.extended;

import bt.protocol.Message;

/**
 * Base class for all extended messages.
 * See BEP-10: Extension Protocol for more details.
 *
 * @since 1.0
 */
public class ExtendedMessage implements Message {

    @Override
    public Integer getMessageId() {
        return ExtendedProtocol.EXTENDED_MESSAGE_ID;
    }
}
