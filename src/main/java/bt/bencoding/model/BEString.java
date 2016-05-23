package bt.bencoding.model;

import bt.bencoding.BEType;

import java.nio.charset.Charset;

public class BEString implements BEObject<byte[]> {

    private byte[] content;

    public BEString(byte[] content) {
        this.content = content;
    }

    @Override
    public BEType getType() {
        return BEType.STRING;
    }

    @Override
    public byte[] getContent() {
        return content;
    }

    @Override
    public byte[] getValue() {
        return content;
    }

    public String getValue(Charset charset) {
        return new String(content, charset);
    }
}
