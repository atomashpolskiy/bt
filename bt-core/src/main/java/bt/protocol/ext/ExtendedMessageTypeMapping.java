package bt.protocol.ext;

import java.util.function.BiConsumer;

public interface ExtendedMessageTypeMapping {

    String getTypeNameForId(Integer typeId);

    void visitMappings(BiConsumer<String, Integer> visitor);
}
