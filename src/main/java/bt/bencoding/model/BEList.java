package bt.bencoding.model;

import bt.bencoding.BEType;

import java.util.List;

public class BEList implements BEObject<List<?>> {

    private byte[] content;
    private List<?> value;

    public BEList(byte[] content, List<?> value) {
        this.content = content;
        this.value = value;
    }

    @Override
    public BEType getType() {
        return BEType.LIST;
    }

    @Override
    public byte[] getContent() {
        return content;
    }

    @Override
    public List<?> getValue() {
        return value;
    }
}
