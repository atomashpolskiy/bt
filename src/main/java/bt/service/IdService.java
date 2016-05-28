package bt.service;

import java.util.Random;

public class IdService implements IIdService {

    private static final int ID_SIZE = 20;

    private final byte[] peerId;

    public IdService() {
        byte[] peerId = new byte[ID_SIZE];
        Random random = new Random(System.currentTimeMillis());
        random.nextBytes(peerId);
        this.peerId = peerId;
    }

    @Override
    public byte[] getPeerId() {
        return peerId;
    }

    @Override
    public byte[] getSecretKey() {
        return null;
    }
}
