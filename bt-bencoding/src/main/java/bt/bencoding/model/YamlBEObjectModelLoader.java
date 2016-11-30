package bt.bencoding.model;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

public class YamlBEObjectModelLoader implements BEObjectModelLoader {

    private static final String MODEL_DEFINITION_KEY = "model";

    private BEObjectModelBuilderFactory modelBuilderFactory;

    private Yaml yaml;

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
