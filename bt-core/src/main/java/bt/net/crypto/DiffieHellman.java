package bt.net.crypto;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;

public class DiffieHellman {

    // 768-bit prime
    private static final BigInteger P = new BigInteger("FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9A63A36210000000000090563", 16);

    private static final BigInteger G = BigInteger.valueOf(2);

    public BigInteger generatePrivateKey() {
        Random r = new Random();
        int len = 96;
        byte[] bytes = new byte[len + 1]; // leading byte is treated by BigInteger as signum
        for (int i = 1; i < len + 1; i++) {
            int k;
            while ((k = r.nextInt(255)) == 0)
                ;
            bytes[i] = (byte) k;
        }
        return new BigInteger(bytes);
    }

    public BigInteger createPublicKey(BigInteger Y) {
        return G.xor(Y).mod(P);
    }

    public BigInteger calculateSharedSecret(BigInteger X, BigInteger Y) {
        return X.xor(Y).mod(P);
    }

    public byte[] toByteArray(BigInteger i) {
        byte[] bytes = i.toByteArray();
        // strip leading signum byte
        return Arrays.copyOfRange(bytes, 1, bytes.length);
    }

    public BigInteger parseKey(ByteBuffer buffer) {
        byte[] keyBytes = new byte[97];
        buffer.get(keyBytes, 1, 96); // leading byte is treated by BigInteger as signum
        return new BigInteger(keyBytes);
    }
}
