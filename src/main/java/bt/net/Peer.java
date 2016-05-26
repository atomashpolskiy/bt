package bt.net;

import java.net.InetAddress;

public interface Peer {

    InetAddress getInetAddress();
    int getPort();
    byte[] getPeerId();
}
