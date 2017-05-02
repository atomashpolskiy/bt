package bt.data.digest;

import bt.BtException;
import bt.data.DataRange;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class JavaSecurityDigester implements Digester {

    private final String algorithm;
    private final int step;

    public JavaSecurityDigester(String algorithm, int step) {
        try {
            // verify that implementation for the algorithm exists
            MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("No such algorithm: " + algorithm, e);
        }
        this.algorithm = algorithm;
        this.step = step;
    }

    @Override
    public byte[] digest(DataRange data) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            // not going to happen
            throw new BtException("Unexpected error", e);
        }

        data.visitUnits((unit, off, lim) -> {
            long remaining = lim - off;
            if (remaining > Integer.MAX_VALUE) {
                throw new BtException("Too much data -- can't read to buffer");
            }
            do {
                digest.update(unit.readBlock(off, Math.min(step, (int) remaining)));
                remaining -= step;
                off += step;
            } while (remaining > 0);

            return true;
        });

        return digest.digest();
    }
}
