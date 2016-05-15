package bt.bencoding;

import java.util.ArrayList;
import java.util.List;

class BEListBuilder extends BEPrefixedTypeBuilder<List<Object>> {

    private final List<Object> objects;
    private BEObjectBuilder<?> builder;

    BEListBuilder() {
        objects = new ArrayList<>();
    }

    @Override
    protected boolean doAccept(char c) {

        if (builder == null) {
            BEType type = BEParser.getTypeForPrefix(c);
            builder = BEParser.builderForType(type);
        }
        if (!builder.accept(c)) {
            objects.add(builder.build());
            builder = null;
            return accept(c);
        }
        return true;
    }

    @Override
    public boolean acceptEOF() {
        return builder == null;
    }

    @Override
    protected List<Object> doBuild() {
        return objects;
    }

    @Override
    public BEType getType() {
        return BEType.LIST;
    }
}
