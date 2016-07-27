package bt.service;

import bt.net.PeerId;

import java.util.Random;

public class IdService implements IIdService {

    private static final int ID_SIZE = 20;

    private final PeerId peerId;

    public IdService() {
        byte[] peerId = new byte[ID_SIZE];
        Random random = new Random(System.currentTimeMillis());
        random.nextBytes(peerId);
        this.peerId = PeerId.fromBytes(peerId);
    }

    @Override
    public PeerId getLocalPeerId() {
        return peerId;
    }

    @Override
    public byte[] getSecretKey() {
        return null;
    }
}
