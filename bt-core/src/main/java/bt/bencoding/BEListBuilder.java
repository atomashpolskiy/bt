package bt.bencoding;

import bt.bencoding.model.BEList;
import bt.bencoding.model.BEObject;

import java.util.ArrayList;
import java.util.List;

class BEListBuilder extends BEPrefixedTypeBuilder<BEList> {

    private final List<BEObject<?>> objects;
    private BEObjectBuilder<? extends BEObject<?>> builder;

    BEListBuilder() {
        objects = new ArrayList<>();
    }

    @Override
    protected boolean doAccept(int b) {

        if (builder == null) {
            BEType type = BEParser.getTypeForPrefix((char) b);
            builder = BEParser.builderForType(type);
        }
        if (!builder.accept(b)) {
            objects.add(builder.build());
            builder = null;
            return accept(b, false);
        }
        return true;
    }

    @Override
    public boolean acceptEOF() {
        return builder == null;
    }

    @Override
    protected BEList doBuild(byte[] content) {
        return new BEList(content, objects);
    }

    @Override
    public BEType getType() {
        return BEType.LIST;
    }
}
