package yourip;

import bt.Bt;
import bt.net.InetPeer;
import bt.net.Peer;
import bt.runtime.BtClient;
import bt.runtime.Config;
import yourip.mock.MockModule;
import yourip.mock.MockStorage;
import yourip.mock.MockTorrent;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class Main {

    private static final int[] ports = new int[] {6891, 6892};
    private static final Set<Peer> peers = new HashSet<Peer>() {{
        for (int port : ports) {
            add(new InetPeer(new InetSocketAddress(port)));
        }
    }};

    public static Set<Peer> peers() {
        return Collections.unmodifiableSet(peers);
    }

    public static void main(String[] args) throws InterruptedException {
        Collection<BtClient> clients = new HashSet<>();
        for (int port : ports) {
            clients.add(buildClient(port));
        }

        clients.forEach(BtClient::startAsync);

        Thread.sleep(10000);

        clients.forEach(BtClient::stop);
    }

    private static BtClient buildClient(int port) {
        Config config = new Config() {
            @Override
            public int getAcceptorPort() {
                return port;
            }
        };

        return Bt.client()
                .config(config)
                .module(YourIPModule.class)
                .module(MockModule.class)
                .storage(new MockStorage())
                .torrent(() -> new MockTorrent())
                .build();
    }

}
