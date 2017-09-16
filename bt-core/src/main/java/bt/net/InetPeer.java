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

package bt.net;

import bt.peer.PeerOptions;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * @since 1.0
 */
public class InetPeer implements Peer {

    private Supplier<InetSocketAddress> addressSupplier;
    private Optional<PeerId> peerId;

    private final PeerOptions options;

    /**
     * @since 1.0
     */
    public InetPeer(InetAddress inetAddress, int port) {
        this(inetAddress, port, null, PeerOptions.defaultOptions());
    }

    /**
     * @since 1.2
     */
    public InetPeer(InetSocketAddress address) {
        this(() -> address, null, PeerOptions.defaultOptions());
    }

    /**
     * @since 1.3
     */
    public InetPeer(InetPeerAddress addressHolder) {
        this(addressHolder::getAddress, null, PeerOptions.defaultOptions());
    }

    /**
     * @since 1.0
     */
    public InetPeer(InetAddress inetAddress, int port, PeerOptions options) {
        this(inetAddress, port, null, options);
    }

    /**
     * @since 1.0
     */
    public InetPeer(InetSocketAddress address, PeerOptions options) {
        this(() -> address, null, options);
    }

    /**
     * @since 1.3
     */
    public InetPeer(InetPeerAddress addressHolder, PeerOptions options) {
        this(addressHolder::getAddress, null, options);
    }

    /**
     * @since 1.0
     */
    public InetPeer(InetAddress inetAddress, int port, PeerId peerId) {
        this(inetAddress, port, peerId, PeerOptions.defaultOptions());
    }

    /**
     * @since 1.2
     */
    public InetPeer(InetSocketAddress address, PeerId peerId) {
        this(() -> address, peerId, PeerOptions.defaultOptions());
    }

    /**
     * @since 1.3
     */
    public InetPeer(InetPeerAddress addressHolder, PeerId peerId) {
        this(addressHolder::getAddress, peerId, PeerOptions.defaultOptions());
    }

    /**
     * @since 1.0
     */
    public InetPeer(InetAddress inetAddress, int port, PeerId peerId, PeerOptions options) {
        this(() -> createAddress(inetAddress, port), peerId, options);
    }

    /**
     * @since 1.2
     */
    public InetPeer(InetSocketAddress address, PeerId peerId, PeerOptions options) {
        this(() -> address, peerId, options);
    }

    /**
     * @since 1.3
     */
    public InetPeer(InetPeerAddress addressHolder, PeerId peerId, PeerOptions options) {
        this(addressHolder::getAddress, peerId, options);
    }

    private InetPeer(Supplier<InetSocketAddress> addressSupplier, PeerId peerId, PeerOptions options) {
        this.addressSupplier = addressSupplier;
        this.peerId = Optional.ofNullable(peerId);
        this.options = options;
    }

    private static InetSocketAddress createAddress(InetAddress inetAddress, int port) {
        if (inetAddress == null || port < 0) {
            throw new IllegalArgumentException("Invalid arguments (address: <" + inetAddress + ":" + port + ">)");
        }
        return new InetSocketAddress(inetAddress, port);
    }

    @Override
    public InetSocketAddress getInetSocketAddress() {
        return addressSupplier.get();
    }

    @Override
    public InetAddress getInetAddress() {
        return addressSupplier.get().getAddress();
    }

    // TODO: probably need an additional property isReachable()
    // to distinguish between outbound and inbound connections
    // and to not send unreachable peers via PEX
    @Override
    public int getPort() {
        return addressSupplier.get().getPort();
    }

    @Override
    public Optional<PeerId> getPeerId() {
        return peerId;
    }

    @Override
    public PeerOptions getOptions() {
        return options;
    }

    @Override
    public int hashCode() {
        return addressSupplier.get().hashCode();
    }

    /**
     * Compares peers by address, regardless of the particular classes.
     */
    @Override
    public boolean equals(Object object) {
        if (object == this) {
            return true;
        }

        if (object == null || !Peer.class.isAssignableFrom(object.getClass())) {
            return false;
        }

        Peer that = (Peer) object;
        return addressSupplier.get().equals(that.getInetSocketAddress());
    }

    @Override
    public String toString() {
        return addressSupplier.get().toString();
    }
}
