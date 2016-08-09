package bt.service;

import bt.net.PeerId;
import bt.tracker.SecretKey;

import java.util.Optional;

public interface IdService {

    PeerId getLocalPeerId();

    Optional<SecretKey> getSecretKey();
}
