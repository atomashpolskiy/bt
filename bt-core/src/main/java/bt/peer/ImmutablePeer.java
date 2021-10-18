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

package bt.peer;

import bt.net.InetPeerAddress;
import bt.net.InetPortUtil;
import bt.net.Peer;
import bt.net.PeerId;
import com.google.common.base.MoreObjects;

import java.net.InetAddress;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * @since 1.0
 */
public class ImmutablePeer implements Peer {

    private final Supplier<InetAddress> addressSupplier;
    private final int port;
    private final Optional<PeerId> peerId;

    private final PeerOptions options;

    private ImmutablePeer(Supplier<InetAddress> addressSupplier, int port, PeerId peerId, PeerOptions options) {
        this.addressSupplier = addressSupplier;
        this.port = port;
        this.peerId = Optional.ofNullable(peerId);
        this.options = options;
    }

    @Override
    public InetAddress getInetAddress() {
        return addressSupplier.get();
    }

    @Override
    public int getPort() {
        return port;
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
    public String toString() {
        MoreObjects.ToStringHelper builder = MoreObjects.toStringHelper(this)
                .add("address", addressSupplier.get())
                .add("port", port);

        peerId.ifPresent(id -> builder.add("peerId", id));

        return builder.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ImmutablePeer immutablePeer = (ImmutablePeer) o;
        return port == immutablePeer.port
                && addressSupplier.get().equals(immutablePeer.addressSupplier.get())
                && peerId.equals(immutablePeer.peerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(addressSupplier.get(), port, peerId);
    }

    public static Builder builder(InetPeerAddress holder) {
        int port = InetPortUtil.checkValidRemotePort(holder.getPort());
        return new Builder(holder::getAddress, port);
    }

    public static Builder builder(InetAddress address, int port) {
        InetPortUtil.checkValidRemotePort(port);
        return new Builder(() -> address, port);
    }

    public static ImmutablePeer build(InetAddress address, int port) {
        return builder(address, port).build();
    }

    public static ImmutablePeer build(InetPeerAddress address) {
        return new Builder(address::getAddress, InetPortUtil.checkValidRemotePort(address.getPort())).build();
    }

    public static class Builder {

        private final Supplier<InetAddress> addressSupplier;
        private final int port;
        private PeerId peerId;
        private PeerOptions options;

        private Builder(Supplier<InetAddress> addressSupplier, int port) {
            this.addressSupplier = addressSupplier;
            this.port = port;
        }

        public Builder peerId(PeerId peerId) {
            this.peerId = Objects.requireNonNull(peerId);
            return this;
        }

        public Builder options(PeerOptions options) {
            this.options = Objects.requireNonNull(options);
            return this;
        }

        public ImmutablePeer build() {
            PeerOptions options = (this.options == null) ?
                    PeerOptions.defaultOptions() : this.options;
            return new ImmutablePeer(addressSupplier, port, peerId, options);
        }
    }
}
