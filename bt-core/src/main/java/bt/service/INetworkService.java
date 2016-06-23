package bt.service;

import java.net.InetAddress;

public interface INetworkService {

    InetAddress getInetAddress();
    int getPort();
}
