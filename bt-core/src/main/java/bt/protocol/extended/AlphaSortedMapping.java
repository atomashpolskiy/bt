package bt.protocol.extended;

import bt.protocol.handler.MessageHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.BiConsumer;

/**
 * Represents a set of mappings,
 * in which numeric IDs are assigned to message type names
 * sequentially starting with 1, in the alphanumeric order of type names.
 *
 *<p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 *
 * @since 1.0
 */
public class AlphaSortedMapping implements ExtendedMessageTypeMapping {

    private Map<Integer, String> nameMap;
    private Map<String, Integer> idMap;
    private Map<Class<?>, String> typeMap;

    /**
     * @since 1.0
     */
    public AlphaSortedMapping(Map<String, MessageHandler<? extends ExtendedMessage>> handlersByTypeName) {

        typeMap = new HashMap<>();

        TreeSet<String> sortedTypeNames = new TreeSet<>();
        handlersByTypeName.forEach((typeName, handler) -> {
            sortedTypeNames.add(typeName);
            handler.getSupportedTypes().forEach(messageType -> {
                typeMap.put(messageType, typeName);
            });
        });

        nameMap = new HashMap<>();
        idMap = new HashMap<>();

        Integer localTypeId = 1;
        for (String typeName : sortedTypeNames) {
            nameMap.put(localTypeId, typeName);
            idMap.put(typeName, localTypeId);
            localTypeId++;
        }
    }

    @Override
    public String getTypeNameForId(Integer typeId) {
        return nameMap.get(Objects.requireNonNull(typeId));
    }

    @Override
    public Integer getIdForTypeName(String typeName) {
        return idMap.get(Objects.requireNonNull(typeName));
    }

    @Override
    public String getTypeNameForJavaType(Class<?> type) {
        return typeMap.get(Objects.requireNonNull(type));
    }

    @Override
    public void visitMappings(BiConsumer<String, Integer> visitor) {
        Objects.requireNonNull(visitor);
        nameMap.forEach((id, name) -> visitor.accept(name, id));
    }
}
