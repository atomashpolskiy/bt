package bt.net;

import java.net.InetAddress;
import java.util.Optional;

public interface Peer {

    InetAddress getInetAddress();

    int getPort();

    Optional<PeerId> getPeerId();

    Origin getOrigin();
}
