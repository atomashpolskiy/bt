package bt.torrent.messaging;

import bt.protocol.Request;
import bt.torrent.annotation.Consumes;
import bt.torrent.data.IDataWorker;

/**
 * Consumes block requests, received from the remote peer.
 *
 * @since 1.0
 */
public class RequestConsumer {

    private IDataWorker dataWorker;

    public RequestConsumer(IDataWorker dataWorker) {
        this.dataWorker = dataWorker;
    }

    @Consumes
    public void consume(Request request, MessageContext context) {
        ConnectionState connectionState = context.getConnectionState();
        if (!connectionState.isChoking()) {
            if (!dataWorker.addBlockRequest(context.getPeer(),
                        request.getPieceIndex(), request.getOffset(), request.getLength())) {
                connectionState.setShouldChoke(true);
            }
        }
    }
}
