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

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Loads model definitions from YAML documents.
 *
 * @since 1.0
 */
public class YamlBEObjectModelLoader implements BEObjectModelLoader {

    private static final String MODEL_DEFINITION_KEY = "model";

    private BEObjectModelBuilderFactory modelBuilderFactory;

    private Yaml yaml;

    /**
     * @since 1.0
     */
    public YamlBEObjectModelLoader() {
        yaml = new Yaml();
        modelBuilderFactory = new DefaultModelBuilderFactory();
    }

    @Override
    public BEObjectModel load(InputStream source) {
        if (source == null) {
            throw new NullPointerException("Missing source -- null");
        }
        return fromYaml(yaml.load(source));
    }

    @SuppressWarnings("raw")
    private BEObjectModel fromYaml(Object yamlObject) {

        if (!List.class.isAssignableFrom(yamlObject.getClass())) {
            throw new IllegalArgumentException("Invalid model -- root document must be a list");
        }

        List entries = (List) yamlObject;
        for (Object entry : entries) {
            if (entry instanceof Map && ((Map) entry).containsKey(MODEL_DEFINITION_KEY)) {
                return buildObjectModel(((Map) entry).get(MODEL_DEFINITION_KEY));
            }
        }
        throw new IllegalArgumentException("Invalid model -- missing model definition");
    }

    private BEObjectModel buildObjectModel(Object modelDefinition) {
        return modelBuilderFactory.getOrCreateBuilder(modelDefinition.getClass())
                .apply(modelDefinition);
    }
}
