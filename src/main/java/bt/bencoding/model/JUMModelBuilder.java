package bt.bencoding.model;

import bt.BtException;
import bt.bencoding.model.rule.ExclusiveRule;
import bt.bencoding.model.rule.RequiredRule;
import bt.bencoding.model.rule.Rule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static bt.bencoding.model.ClassUtil.cast;
import static bt.bencoding.model.ClassUtil.castList;
import static bt.bencoding.model.ClassUtil.readNotNull;

public class JUMModelBuilder implements BEObjectModelBuilder<Map> {

    private static final String TYPE_KEY = "type";
    private static final String REQUIRED_KEY = "required";
    private static final String EXCLUSIVES_KEY = "exclusives";
    private static final String MAP_ENTRIES_KEY = "entries";
    private static final String MAP_ENTRY_KEY_KEY = "key";
    private static final String LIST_ELEMENTS_KEY = "elements";

    @Override
    public BEObjectModel buildModel(Map map) {

        try {
            String sourceType = readType(map);
            switch (sourceType) {
                case "dictionary": {
                    return buildMap(map);
                }
                case "list": {
                    return buildList(map);
                }
                case "binary": {
                    return new BEStringModel(true, Collections.emptyList());
                }
                case "string": {
                    return new BEStringModel(false, Collections.emptyList());
                }
                case "integer": {
                    return new BEIntegerModel(Collections.emptyList());
                }
                default: {
                    throw new BtException("Unsupported BE type: " + sourceType);
                }
            }
        } catch (Exception e) {
            throw new BtException("Failed to build BE model", e);
        }
    }

    private String readType(Map map) {
        try {
            return readNotNull(map, String.class, TYPE_KEY).toLowerCase();
        } catch (Exception e) {
            throw new BtException("Failed to read type", e);
        }
    }

    private BEObjectModel buildMap(Map map) throws Exception {

        List<Map> entries = castList(Map.class, readNotNull(map, List.class, MAP_ENTRIES_KEY));
        Map<String, BEObjectModel> entriesModel = new HashMap<>(entries.size() + 1);
        for (Map entry : entries) {
            String key = readNotNull(entry, String.class, MAP_ENTRY_KEY_KEY);
            entriesModel.put(key, buildModel(entry));
        }
        return new BEMapModel(entriesModel, buildMapValidation(map, entries));
    }

    private List<Rule> buildMapValidation(Map map, List<Map> entries) throws Exception {

        List<Rule> rules = new ArrayList<>(2);

        List<String> required = entries.stream()
                .filter(this::isRequired)
                .map(entry -> {
                    try {
                        return readNotNull(entry, String.class, MAP_ENTRY_KEY_KEY);
                    } catch (Exception e) {
                        throw new BtException("Unexpected error", e);
                    }
                })
                .collect(Collectors.toList());

        List<String> exclusives = castList(String.class, cast(List.class, EXCLUSIVES_KEY, map.get(EXCLUSIVES_KEY)));
        if (exclusives != null) {
            rules.add(new ExclusiveRule(exclusives, required));
        } else {
            rules.add(new RequiredRule(required));
        }

        return rules.isEmpty()? Collections.emptyList() : rules;
    }

    private boolean isRequired(Map map) {
        try {
            return (map.get(REQUIRED_KEY) == null) ?
                    true : cast(Boolean.class, REQUIRED_KEY, map.get(REQUIRED_KEY));
        } catch (Exception e) {
            throw new BtException("Failed to check if object is required", e);
        }
    }

    private BEListModel buildList(Map map) throws Exception {

        Map elements = readNotNull(map, Map.class, LIST_ELEMENTS_KEY);
        BEObjectModel elementModel = buildModel(elements);
        return new BEListModel(elementModel, Collections.emptyList());
    }

    @Override
    public Class<Map> getSourceType() {
        return Map.class;
    }
}
