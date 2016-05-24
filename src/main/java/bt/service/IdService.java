package bt.service;

import java.util.Random;

public class IdService implements IIdService {

    private static final int ID_SIZE = 20;

    @Override
    public byte[] getPeerId() {
        byte[] peerId = new byte[ID_SIZE];
        Random random = new Random(System.currentTimeMillis());
        random.nextBytes(peerId);
        return peerId;
    }

    @Override
    public byte[] getSecretKey() {
        return null;
    }
}
