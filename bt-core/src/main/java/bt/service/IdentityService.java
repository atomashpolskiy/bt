package bt.service;

import bt.net.PeerId;
import bt.tracker.SecretKey;

import java.util.Optional;

/**
 * Identity service.
 *
 * @since 1.0
 */
public interface IdentityService {

    /**
     * @return Peer ID used by current runtime
     */
    PeerId getLocalPeerId();

    // TODO: seems like it should be taken from the metainfo file
    /**
     * @return Secret key used for interaction with HTTP trackers
     * @since 1.0
     */
    Optional<SecretKey> getSecretKey();
}
