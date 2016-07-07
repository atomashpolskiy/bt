package bt.bencoding.model;

import bt.bencoding.BEEncoder;
import bt.bencoding.BEType;

import java.io.OutputStream;
import java.math.BigInteger;
import java.util.Arrays;

public class BEInteger implements BEObject<BigInteger> {

    private byte[] content;
    private BigInteger value;
    private BEEncoder encoder;

    public BEInteger(byte[] content, BigInteger value) {
        this.content = content;
        this.value = value;
        encoder = BEEncoder.encoder();
    }

    @Override
    public BEType getType() {
        return BEType.INTEGER;
    }

    @Override
    public byte[] getContent() {
        return Arrays.copyOf(content, content.length);
    }

    @Override
    public BigInteger getValue() {
        return value;
    }

    @Override
    public void writeTo(OutputStream out) {
        encoder.encode(this, out);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public boolean equals(Object obj) {

        if (obj == null || !(obj instanceof BEInteger)) {
            return false;
        }

        if (obj == this) {
            return true;
        }

        return value.equals(((BEInteger) obj).getValue());
    }
}
