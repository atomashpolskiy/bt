package bt.tracker;

import bt.BtException;
import bt.net.InetPeer;
import bt.net.Peer;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Wrapper for binary representation of a list of peers,
 * which is used by the majority of HTTP trackers and all UDP trackers.
 * See BEP-23 for more details.
 *
 * Decoding is performed lazily when {@link Iterable#iterator()} is used.
 * Results are cached, so it's only done once per instance of this class.
 *
 * @since 1.0
 */
public class CompactPeerInfo implements Iterable<Peer> {

    /**
     * Address family
     *
     * @since 1.0
     */
    public enum AddressType {

        /**
         * Internet Protocol v4
         *
         * @since 1.0
         */
        IPV4(4),

        /**
         * Internet Protocol v6
         *
         * @since 1.0
         */
        IPV6(16);

        private int length;

        AddressType(int length) {
            this.length = length;
        }

        /**
         * @return Address length in bytes
         * @since 1.0
         */
        public int length() {
            return length;
        }
    }

    private static final int PORT_LENGTH = 2;
    private final int addressLength;

    private final byte[] peers;
    private final List<Peer> peerList;

    /**
     * Create a list of peers from its' binary representation,
     * using the specified address type for decoding individual addresses.
     *
     * @since 1.0
     */
    public CompactPeerInfo(byte[] peers, AddressType addressType) {

        Objects.requireNonNull(peers);
        Objects.requireNonNull(addressType);

        int peerLength = addressType.length() + PORT_LENGTH;
        if (peers.length % peerLength != 0) {
            throw new BtException("Invalid peers string (" + addressType.name() + ") -- length (" +
                    peers.length + ") is not divisible by " + peerLength);
        }
        addressLength = addressType.length();
        this.peers = peers;

        peerList = new ArrayList<>();
    }

    @Override
    public Iterator<Peer> iterator() {

        if (!peerList.isEmpty()) {
            return peerList.iterator();
        }

        return new Iterator<Peer>() {

            private int i;

            @Override
            public boolean hasNext() {
                return i < peers.length;
            }

            @Override
            public Peer next() {

                if (!hasNext()) {
                    throw new NoSuchElementException("No more peers left");
                }

                int from, to;
                InetAddress inetAddress;
                int port;

                from = i;
                to = i = i + addressLength;
                try {
                    inetAddress = InetAddress.getByAddress(Arrays.copyOfRange(peers, from, to));
                } catch (UnknownHostException e) {
                    throw new BtException("Failed to get next peer", e);
                }

                from = to;
                to = i = i + PORT_LENGTH;
                port = (((peers[from] << 8) & 0xFF00) + (peers[to - 1] & 0x00FF));

                Peer peer = new InetPeer(inetAddress, port);
                peerList.add(peer);
                return peer;
            }
        };
    }
}
