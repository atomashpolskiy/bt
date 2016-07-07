package bt.bencoding;

import bt.bencoding.model.BEMap;
import bt.bencoding.model.BEObject;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

class BEMapBuilder extends BEPrefixedTypeBuilder<BEMap> {

    private final Map<String, BEObject<?>> map;
    private BEStringBuilder keyBuilder;
    private BEObjectBuilder<? extends BEObject<?>> valueBuilder;

    private Charset keyCharset;

    BEMapBuilder() {
        map = new HashMap<>();
        keyCharset = Charset.forName("UTF-8");
    }

    @Override
    protected boolean doAccept(int b) {

        if (keyBuilder == null) {
            keyBuilder = new BEStringBuilder();
        }
        if (valueBuilder == null) {
            if (!keyBuilder.accept(b)) {
                BEType valueType = BEParser.getTypeForPrefix((char) b);
                valueBuilder = BEParser.builderForType(valueType);
                return valueBuilder.accept(b);
            }
        } else {
            if (!valueBuilder.accept(b)) {
                map.put(keyBuilder.build().getValue(keyCharset), valueBuilder.build());
                keyBuilder = null;
                valueBuilder = null;
                return accept(b, false);
            }
        }
        return true;
    }

    @Override
    protected BEMap doBuild(byte[] content) {
        return new BEMap(content, map);
    }

    @Override
    protected boolean acceptEOF() {
        return keyBuilder == null && valueBuilder == null;
    }

    @Override
    public BEType getType() {
        return BEType.MAP;
    }
}
