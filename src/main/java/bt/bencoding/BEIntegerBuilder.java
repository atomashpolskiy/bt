package bt.bencoding;

import java.math.BigInteger;

class BEIntegerBuilder extends BEPrefixedTypeBuilder<BigInteger> {

    private StringBuilder buf;

    BEIntegerBuilder() {
        buf = new StringBuilder();
    }

    @Override
    protected boolean doAccept(char c) {

        if (Character.isDigit(c) || buf.length() == 0 && c == '-') {
            buf.append(c);
            return true;
        }
        throw new IllegalArgumentException("Unexpected token while reading integer: " + c);
    }

    @Override
    public boolean acceptEOF() {
        return true;
    }

    @Override
    public BEType getType() {
        return BEType.INTEGER;
    }

    @Override
    public BigInteger doBuild() {
        return new BigInteger(buf.toString());
    }
}
