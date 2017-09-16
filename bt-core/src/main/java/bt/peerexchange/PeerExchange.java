/*
 * Copyright (c) 2016—2017 Andrei Tomashpolskiy and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package bt.peerexchange;

import bt.BtException;
import bt.bencoding.model.BEMap;
import bt.bencoding.model.BEObject;
import bt.bencoding.model.BEString;
import bt.net.Peer;
import bt.protocol.InvalidMessageException;
import bt.protocol.crypto.EncryptionPolicy;
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
import java.util.Map;
import java.util.stream.Collectors;

class PeerExchange extends ExtendedMessage {

    private static final String ADDED_IPV4_KEY = "added";
    private static final String ADDED_IPV4_FLAGS_KEY = "added.f";
    private static final String ADDED_IPV6_KEY = "added6";
    private static final String ADDED_IPV6_FLAGS_KEY = "added6.f";
    private static final String DROPPED_IPV4_KEY = "dropped";
    private static final String DROPPED_IPV6_KEY = "dropped6";

    private static final int CRYPTO_FLAG = 0x01;

    public static Builder builder() {
        return new Builder();
    }

    public static PeerExchange parse(BEMap message) {
        Map<String, BEObject<?>> m = message.getValue();

        Collection<Peer> added = new HashSet<>();
        extractPeers(m, ADDED_IPV4_KEY, ADDED_IPV4_FLAGS_KEY, AddressType.IPV4, added);
        extractPeers(m, ADDED_IPV6_KEY, ADDED_IPV6_FLAGS_KEY, AddressType.IPV6, added);

        Collection<Peer> dropped = new HashSet<>();
        extractPeers(m, DROPPED_IPV4_KEY, null, AddressType.IPV4, dropped);
        extractPeers(m, DROPPED_IPV6_KEY, null, AddressType.IPV6, dropped);

        return new PeerExchange(added, dropped);
    }

    private static void extractPeers(Map<String, BEObject<?>> m,
                              String peersKey,
                              String flagsKey,
                              AddressType addressType,
                              Collection<Peer> destination) {
        if (m.containsKey(peersKey)) {
            byte[] peers = ((BEString) m.get(peersKey)).getValue();
            if (flagsKey != null && m.containsKey(flagsKey)) {
                byte[] flags = ((BEString) m.get(flagsKey)).getValue();
                extractPeers(peers, flags, addressType, destination);
            } else {
                extractPeers(peers, addressType, destination);
            }
        }
    }

    private static void extractPeers(byte[] peers, byte[] flags, AddressType addressType, Collection<Peer> destination) {
        byte[] cryptoFlags = new byte[flags.length];
        for (int i = 0; i < flags.length; i++) {
            cryptoFlags[i] = (byte) (flags[i] & CRYPTO_FLAG);
        }
        new CompactPeerInfo(peers, addressType, cryptoFlags).iterator().forEachRemaining(destination::add);
    }

    private static void extractPeers(byte[] peers, AddressType addressType, Collection<Peer> destination) {
        new CompactPeerInfo(peers, addressType).iterator().forEachRemaining(destination::add);
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
                Collection<Peer> inet4Peers = filterByAddressType(added, AddressType.IPV4);
                Collection<Peer> inet6Peers = filterByAddressType(added, AddressType.IPV6);

                put(ADDED_IPV4_KEY, encodePeers(inet4Peers));
                put(ADDED_IPV4_FLAGS_KEY, encodePeerOptions(inet4Peers));
                put(ADDED_IPV6_KEY, encodePeers(inet6Peers));
                put(ADDED_IPV6_FLAGS_KEY, encodePeerOptions(inet6Peers));

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

    private static BEString encodePeerOptions(Collection<Peer> peers) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        for (Peer peer : peers) {
            byte options = 0;
            EncryptionPolicy encryptionPolicy = peer.getOptions().getEncryptionPolicy();
            if (encryptionPolicy == EncryptionPolicy.PREFER_ENCRYPTED
                    || encryptionPolicy == EncryptionPolicy.REQUIRE_ENCRYPTED) {
                options |= CRYPTO_FLAG;
            }
            bos.write(options);
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
