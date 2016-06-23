package bt.bencoding.model;

import bt.bencoding.BEType;

import java.math.BigInteger;

public class BEInteger implements BEObject<BigInteger> {

    private byte[] content;
    private BigInteger value;

    public BEInteger(byte[] content, BigInteger value) {
        this.content = content;
        this.value = value;
    }

    @Override
    public BEType getType() {
        return BEType.INTEGER;
    }

    @Override
    public byte[] getContent() {
        return content;
    }

    @Override
    public BigInteger getValue() {
        return value;
    }
}
