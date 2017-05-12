package bt.net.crypto;

import javax.crypto.spec.DHPublicKeySpec;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.Key;
import java.security.KeyFactory;

public class DiffieHellman {

    // 768-bit prime
    private static final BigInteger P = new BigInteger("FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD1290" +
            "24E088A67CC74020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245E" +
            "485B576625E7EC6F44C42E9A63A36210000000000090563", 16);

    private static final BigInteger G = BigInteger.valueOf(2);

    public Key generateKey(BigInteger Y) {
        try {
            KeyFactory factory = KeyFactory.getInstance("DiffieHellman");
            return factory.generatePublic(new DHPublicKeySpec(Y, P, G));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public BigInteger calculateSharedSecret(BigInteger X, BigInteger Y) {
        return X.xor(Y).mod(P);
    }

    public BigInteger parseKey(ByteBuffer buffer) {
        byte[] keyBytes = new byte[96];
        buffer.get(keyBytes);
        return new BigInteger(keyBytes);
    }
}
