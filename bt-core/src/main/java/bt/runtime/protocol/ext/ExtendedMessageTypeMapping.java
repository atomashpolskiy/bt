package bt.runtime.protocol.ext;

import java.util.function.BiConsumer;

public interface ExtendedMessageTypeMapping {

    String getTypeNameForId(Integer typeId);

    Integer getIdForTypeName(String typeName);

    String getTypeNameForJavaType(Class<?> type);

    void visitMappings(BiConsumer<String, Integer> visitor);
}
