package bt.peerexchange.protocol;

import bt.BtException;
import bt.bencoding.model.BEMap;
import bt.bencoding.model.BEObject;
import bt.bencoding.model.BEString;
import bt.net.Peer;
import bt.protocol.InvalidMessageException;
import bt.protocol.extended.ExtendedMessage;
import bt.tracker.CompactPeerInfo;
import bt.tracker.CompactPeerInfo.AddressType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.stream.Collectors;

public class PeerExchange extends ExtendedMessage {

    private static final String ADDED_IPV4_KEY = "added";
    private static final String ADDED_IPV6_KEY = "added6";
    private static final String DROPPED_IPV4_KEY = "dropped";
    private static final String DROPPED_IPV6_KEY = "dropped6";

    public static Builder builder() {
        return new Builder();
    }

    public static PeerExchange parse(BEMap message) {

        Collection<Peer> added = new HashSet<>();
        extractPeers((BEString) message.getValue().get(ADDED_IPV4_KEY), added, AddressType.IPV4);
        extractPeers((BEString) message.getValue().get(ADDED_IPV6_KEY), added, AddressType.IPV6);

        Collection<Peer> dropped = new HashSet<>();
        extractPeers((BEString) message.getValue().get(DROPPED_IPV4_KEY), dropped, AddressType.IPV4);
        extractPeers((BEString) message.getValue().get(DROPPED_IPV6_KEY), dropped, AddressType.IPV6);

        return new PeerExchange(added, dropped);
    }

    private static void extractPeers(BEString source, Collection<Peer> destination, AddressType addressType) {
        if (source == null) {
            return;
        }
        new CompactPeerInfo(source.getValue(), addressType).iterator()
                .forEachRemaining(destination::add);
    }

    private Collection<Peer> added;
    private Collection<Peer> dropped;

    private BEMap message;

    public PeerExchange(Collection<Peer> added, Collection<Peer> dropped) {

        if (added.isEmpty() && dropped.isEmpty()) {
            throw new InvalidMessageException("Can't create PEX message: no peers added/dropped");
        }
        this.added = Collections.unmodifiableCollection(added);
        this.dropped = Collections.unmodifiableCollection(dropped);
    }

    public Collection<Peer> getAdded() {
        return added;
    }

    public Collection<Peer> getDropped() {
        return dropped;
    }

    void writeTo(OutputStream out) throws IOException {

        if (message == null) {
            message = new BEMap(null, new HashMap<String, BEObject<?>>() {{
                put(ADDED_IPV4_KEY, encodePeers(filterByAddressType(added, AddressType.IPV4)));
                put(ADDED_IPV6_KEY, encodePeers(filterByAddressType(added, AddressType.IPV6)));
                put(DROPPED_IPV4_KEY, encodePeers(filterByAddressType(dropped, AddressType.IPV4)));
                put(DROPPED_IPV6_KEY, encodePeers(filterByAddressType(dropped, AddressType.IPV6)));
            }});
        }
        message.writeTo(out);
    }

    private static Collection<Peer> filterByAddressType(Collection<Peer> peers, AddressType addressType) {
        return peers.stream()
            .filter(peer -> peer.getInetAddress().getAddress().length == addressType.length())
            .collect(Collectors.toList());
    }

    private static BEString encodePeers(Collection<Peer> peers) {

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        for (Peer peer : peers) {
            try {
                bos.write(peer.getInetAddress().getAddress());
                bos.write((peer.getPort() & 0xFF00) >> 8);
                bos.write(peer.getPort() & 0x00FF);

            } catch (IOException e) {
                // won't happen
                throw new BtException("Unexpected I/O exception", e);
            }
        }
        return new BEString(bos.toByteArray());
    }

    public static class Builder {

        private Collection<Peer> added;
        private Collection<Peer> dropped;

        private Builder() {
            added = new HashSet<>();
            dropped = new HashSet<>();
        }

        public Builder added(Peer peer) {
            added.add(peer);
            dropped.remove(peer);
            return this;
        }

        public Builder dropped(Peer peer) {
            dropped.add(peer);
            added.remove(peer);
            return this;
        }

        public PeerExchange build() {
            return new PeerExchange(added, dropped);
        }
    }

    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "] added peers {" + added + "}, dropped peers {" + dropped + "}";
    }
}
