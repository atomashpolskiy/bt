package bt.bencoding.model;

import bt.bencoding.BEType;

import java.util.Map;

public class BEMap implements BEObject<Map<String, BEObject>> {

    private byte[] content;
    private Map<String, BEObject> value;

    public BEMap(byte[] content, Map<String, BEObject> value) {
        this.content = content;
        this.value = value;
    }

    @Override
    public BEType getType() {
        return BEType.MAP;
    }

    @Override
    public byte[] getContent() {
        return content;
    }

    @Override
    public Map<String, BEObject> getValue() {
        return value;
    }
}
