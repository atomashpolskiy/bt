package bt.data.digest;

import bt.data.range.ByteRange;
import org.junit.Test;

import java.security.MessageDigest;

import static bt.TestUtil.sequence;
import static org.junit.Assert.assertArrayEquals;

public class JavaSecurityDigesterTest {
    private static final String algorithm = "SHA-1";

    private final MessageDigest digest;

    public JavaSecurityDigesterTest() throws Exception {
        this.digest = MessageDigest.getInstance(algorithm);
    }

    @Test
    public void testDigester_DataLengthEqualToStep() {
        int len = 10000;
        byte[] data = sequence(len);
        byte[] hash = digest.digest(data);

        JavaSecurityDigester digester = new JavaSecurityDigester(algorithm, len);
        assertArrayEquals(hash, digester.digest(new ByteRange(data)));
    }

    @Test
    public void testDigester_DataLengthLessThanStep() {
        int len = 10000;
        byte[] data = sequence(len);
        byte[] hash = digest.digest(data);

        JavaSecurityDigester digester = new JavaSecurityDigester(algorithm, len + 1);
        assertArrayEquals(hash, digester.digest(new ByteRange(data)));
    }

    @Test
    public void testDigester_DataLengthFactorOfStep() {
        int len = 10000;
        byte[] data = sequence(len);
        byte[] hash = digest.digest(data);

        JavaSecurityDigester digester = new JavaSecurityDigester(algorithm, len / 10);
        assertArrayEquals(hash, digester.digest(new ByteRange(data)));
    }

    @Test
    public void testDigester_DataLengthMoreThanStep() {
        int len = 10000;
        byte[] data = sequence(len);
        byte[] hash = digest.digest(data);

        JavaSecurityDigester digester = new JavaSecurityDigester(algorithm, len / 7);
        assertArrayEquals(hash, digester.digest(new ByteRange(data)));
    }
}
