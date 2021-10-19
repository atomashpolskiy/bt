package bt.net.peer;

import bt.bencoding.model.BEObject;
import bt.bencoding.types.BEInteger;
import bt.net.InetPortUtil;
import bt.net.Peer;
import bt.net.PeerId;
import bt.protocol.InvalidMessageException;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.Maps;
import com.google.common.primitives.Ints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.Map;
import java.util.Optional;

/**
 * This class represents a peer past the discovery stage, that we are in the process of establishing a connection with
 * it, or have successfully connected to it.
 * <p>
 * The port field may be mutated for incoming connections, because on incoming connections, the remote listening port
 * is not known, unless the peer shares it with an extended handshake (BEP-0010). This is imported for PEx (BEP-0011)
 * because we cannot share a peer's IP/port if we do not know the remote port.
 * <p>
 * Because this class is mutated, it does not have a {@link #hashCode()} or {@link #equals(Object)} methods. For this
 * reason, should not be put in map, except for when the {@link System#identityHashCode(Object)} provides the required
 * behavior
 */
public class InetPeer {
    private static final Logger logger = LoggerFactory.getLogger(InetPeer.class);

    public static final int UNKNOWN_PORT = -1;

    private final InetAddress address;
    private final boolean incoming;

    // may be mutated
    private volatile int port;
    private volatile PeerId peerId;
    // maps each supported extension to an extended message id. See BEP-0010
    private volatile BiMap<Integer, String> extensionMap = ImmutableBiMap.of();
    // a lock for the extensionMap to ensure two threads don't try to update it at once.
    private final Object extensionMapLock = new Object();

    /**
     * Construct a new peer to connect to from the Peer source. This is always an outgoing connection
     *
     * @param peer the peer to connect to
     */
    public InetPeer(Peer peer) {
        this(peer.getInetAddress(), peer.getPort(), false);
    }

    /**
     * Construct a new peer we received a connection from. The port is not yet known.
     *
     * @param address the address of the incoming peer
     */
    public InetPeer(InetAddress address) {
        this(address, UNKNOWN_PORT, true);
    }

    private InetPeer(InetAddress address, int port, boolean incoming) {
        this.address = address;
        this.port = port;
        this.incoming = incoming;
    }

    public InetAddress getInetAddress() {
        return address;
    }

    public void setPort(int newPort) {
        InetPortUtil.checkValidRemotePort(newPort);
        if (port != UNKNOWN_PORT && port != newPort) {
            throw new IllegalStateException("Port already set to: " + port + "." +
                    " Attempted to update to: " + newPort);
        }
        port = newPort;
    }

    public Optional<PeerId> getPeerId() {
        return Optional.ofNullable(peerId);
    }

    public void setPeerId(PeerId peerId) {
        this.peerId = peerId;
    }

    /**
     * @return Peer's listening port or {@link InetPeer#UNKNOWN_PORT}, if it's not known yet
     * (e.g. when the connection is incoming and the remote side hasn't
     * yet communicated to us its' listening port via extended handshake)
     * @since 1.0
     */
    public int getPort() {
        return port;
    }

    /**
     * Check whether the connection to this peer was incoming, or outgoing
     *
     * @return true if this peer connection was initiated from an incoming connection. False otherwise
     */
    public boolean isIncoming() {
        return incoming;
    }

    /**
     * @return true, if the peer's listening port is not known yet
     * @see #getPort()
     * @since 1.9
     */
    public boolean isPortUnknown() {
        return port == UNKNOWN_PORT;
    }

    /**
     * Get the extension map for this peer to decode extension messages
     *
     * @return the extension map for this peer to decode extension messages
     */
    public BiMap<Integer, String> getExtensionMap() {
        return extensionMap;
    }

    /**
     * Get the extension map for this peer to decode extension messages
     *
     * @return the extension map for this peer to decode extension messages
     */
    public boolean supportsExtension(String extension) {
        return extensionMap.containsValue(extension);
    }

    /**
     * Update the extension map for this peer (BEP-0010)
     *
     * @param changes the additive changes to the extension map
     */
    public void updateExtensionMap(Map<String, BEObject<?>> changes) {
        if (changes.isEmpty())
            return;
        synchronized (extensionMapLock) {
            // copy on write in case a different thread is reading this map.
            BiMap<Integer, String> newMapping = HashBiMap.create(extensionMap);
            for (Map.Entry<String, BEObject<?>> entry : changes.entrySet()) {
                String typeName = entry.getKey();
                try {
                    int typeId = Ints.checkedCast(((BEInteger) entry.getValue()).longValueExact());
                    // by setting type ID to 0 peer signals that he has disabled this extension
                    if (typeId == 0) {
                        newMapping.inverse().remove(typeName);
                    } else {
                        newMapping.forcePut(typeId, typeName);
                    }
                } catch (Exception ex) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Peer {}:{} sent a bad dictionary for protocol extensions (BEP-0010) with key {}", address, port, typeName);
                        logger.trace("PeerID: {}", peerId, ex);
                    }
                    throw new InvalidMessageException("Bad dictionary for protocol extensions (BEP-0010) with key "+ typeName);
                }
            }

            this.extensionMap = Maps.unmodifiableBiMap(newMapping);
        }
    }
}
