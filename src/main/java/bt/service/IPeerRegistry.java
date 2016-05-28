package bt.service;

import bt.net.Peer;

import java.net.InetAddress;

public interface IPeerRegistry {

    Peer getLocalPeer();

    Peer getOrCreatePeer(InetAddress inetAddress, int port);
}
