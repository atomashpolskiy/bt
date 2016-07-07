package bt.protocol.ext;

import com.google.inject.Inject;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.BiConsumer;

public class AlphaSortedMessageTypeMapping implements ExtendedMessageTypeMapping {

    private Map<Integer, String> nameMap;

    @Inject
    public AlphaSortedMessageTypeMapping(Map<String, ExtendedMessageHandler<?>> handlersByTypeName) {

        TreeSet<String> sortedTypeNames = new TreeSet<>();
        handlersByTypeName.keySet().forEach(sortedTypeNames::add);

        nameMap = new HashMap<>();
        Integer localTypeId = 1;
        for (String typeName : sortedTypeNames) {
            nameMap.put(localTypeId, typeName);
            localTypeId++;
        }
    }

    @Override
    public String getTypeNameForId(Integer typeId) {
        return nameMap.get(Objects.requireNonNull(typeId));
    }

    @Override
    public void visitMappings(BiConsumer<String, Integer> visitor) {
        Objects.requireNonNull(visitor);
        nameMap.forEach((id, name) -> visitor.accept(name, id));
    }
}
