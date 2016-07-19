package bt.service;

import bt.net.Peer;

import java.util.Collection;

public interface PeerSource {

    boolean isRefreshable();

    boolean refresh();

    Collection<Peer> query();
}
