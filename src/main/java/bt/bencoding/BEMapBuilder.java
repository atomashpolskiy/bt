package bt.bencoding;

import java.util.HashMap;
import java.util.Map;

class BEMapBuilder extends BEPrefixedTypeBuilder<Map<String, Object>> {

    private final Map<String, Object> map;
    private BEStringBuilder keyBuilder;
    private BEObjectBuilder<?> valueBuilder;

    BEMapBuilder() {
        map = new HashMap<>();
    }

    @Override
    protected boolean doAccept(char c) {

        if (keyBuilder == null) {
            keyBuilder = new BEStringBuilder();
        }
        if (valueBuilder == null) {
            if (!keyBuilder.accept(c)) {
                BEType valueType = BEParser.getTypeForPrefix(c);
                valueBuilder = BEParser.builderForType(valueType);
                return valueBuilder.accept(c);
            }
        } else {
            if (!valueBuilder.accept(c)) {
                map.put(keyBuilder.build(), valueBuilder.build());
                keyBuilder = null;
                valueBuilder = null;
                return accept(c);
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
