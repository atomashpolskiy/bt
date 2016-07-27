package bt.service;

import bt.net.PeerId;

public interface IIdService {

    PeerId getLocalPeerId();
    byte[] getSecretKey();
}
