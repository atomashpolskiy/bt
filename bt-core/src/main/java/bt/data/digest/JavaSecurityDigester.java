package bt.data.digest;

import bt.BtException;
import bt.data.DataRange;
import bt.data.range.Range;

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

    public String getAlgorithm() {
        return algorithm;
    }

    @Override
    public byte[] digest(DataRange data) {
        MessageDigest digest = createDigest();

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

    @Override
    public byte[] digest(Range<?> data) {
        MessageDigest digest = createDigest();

        long len = data.length();
        if (len <= step) {
            digest.update(data.getBytes());
        } else {
            for (long i = 0; i < len; i += step) {
                digest.update(data.getSubrange(i, Math.min((len - i), step)).getBytes());
            }
        }
        return digest.digest();
    }

    private MessageDigest createDigest() {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            // not going to happen
            throw new BtException("Unexpected error", e);
        }
    }
}
