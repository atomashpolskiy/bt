package bt.bencoding;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

class BEMapBuilder extends BEPrefixedTypeBuilder<Map<String, Object>> {

    private final Map<String, Object> map;
    private BEStringBuilder keyBuilder;
    private BEObjectBuilder<?> valueBuilder;

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
                map.put(new String(keyBuilder.build(), keyCharset), valueBuilder.build());
                keyBuilder = null;
                valueBuilder = null;
                return accept(b);
            }
        }
        return true;
    }

    @Override
    protected Map<String, Object> doBuild() {
        return map;
    }

    @Override
    public boolean acceptEOF() {
        return keyBuilder == null && valueBuilder == null;
    }

    @Override
    public BEType getType() {
        return BEType.MAP;
    }
}
