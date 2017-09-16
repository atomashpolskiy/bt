/*
 * Copyright (c) 2016—2017 Andrei Tomashpolskiy and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package bt.bencoding.model;

import bt.bencoding.model.rule.ExclusiveRule;
import bt.bencoding.model.rule.RequiredRule;
import bt.bencoding.model.rule.Rule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static bt.bencoding.model.ClassUtil.cast;
import static bt.bencoding.model.ClassUtil.castList;
import static bt.bencoding.model.ClassUtil.read;
import static bt.bencoding.model.ClassUtil.readNotNull;

/**
 * Builds an object model from a {@link java.util.Map}-based definition.
 *
 * @since 1.0
 */
public class JUMModelBuilder implements BEObjectModelBuilder<Map> {

    private static final String TYPE_KEY = "type";
    private static final String REQUIRED_KEY = "required";
    private static final String EXCLUSIVES_KEY = "exclusives";
    private static final String MAP_ENTRIES_KEY = "entries";
    private static final String MAP_ENTRY_KEY_KEY = "key";
    private static final String LIST_ELEMENTS_KEY = "elements";

    // TODO: allow defining custom types in separate files and re-use
    @Override
    public BEObjectModel buildModel(Map map) {
        // try reading custom object type and if it's not there resort to default types
        return readObjectModel(map).orElseGet(() -> {
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
                        throw new IllegalArgumentException("Unsupported BE type: " + sourceType);
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to build BE model", e);
            }
        });
    }

    private Optional<BEObjectModel> readObjectModel(Map map) {
        try {
            Object objectModel = read(map, Object.class, TYPE_KEY);
            if (objectModel == null || !Map.class.isAssignableFrom(objectModel.getClass())) {
                return Optional.empty();
            } else {
                return Optional.of(buildModel((Map) objectModel));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to read type", e);
        }
    }

    private String readType(Map map) {
        try {
            return readNotNull(map, String.class, TYPE_KEY).toLowerCase();
        } catch (Exception e) {
            throw new RuntimeException("Failed to read type", e);
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
                        throw new RuntimeException("Unexpected error", e);
                    }
                })
                .collect(Collectors.toList());

        @SuppressWarnings("unchecked")
        List<Object> exclusives = (List<Object>) cast(List.class, EXCLUSIVES_KEY, map.get(EXCLUSIVES_KEY));
        if (exclusives != null) {
            Collection<Set<String>> exclusiveSets = exclusives.stream()
                .map(item -> {
                    if (item instanceof String) {
                        return Collections.singleton((String) item);
                    } else {
                        try {
                            List<String> strings = castList(String.class, cast(List.class, null, item));
                            return new HashSet<>(strings);
                        } catch (Exception e) {
                            throw new RuntimeException("Unexpected error", e);
                        }
                    }
                })
                .collect(Collectors.toList());

            rules.add(new ExclusiveRule(exclusiveSets, required));
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
            throw new RuntimeException("Failed to check if object is required", e);
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
