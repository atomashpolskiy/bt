package bt.net;

import bt.metainfo.TorrentId;
import bt.protocol.Message;

import java.util.function.Consumer;
import java.util.function.Supplier;

public interface PeerActivityListener {

    void onPeerConnected(TorrentId torrentId, Peer peer, Consumer<Consumer<Message>> messageConsumers,
                         Consumer<Supplier<Message>> messageSuppliers);

    void onPeerDisconnected(Peer peer);
}
