package bt.bencoding.model;

import bt.bencoding.BEParser;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class YamlBEObjectModelLoaderTest {

    BEObjectModel model, model_v2;

    @Before
    public void setUp() {
        model = loadModel("/metainfo.yml");
        model_v2 = loadModel("/metainfo_v2.yml");
    }

    private BEObjectModel loadModel(String name) {
        try {
            try (InputStream in = YamlBEObjectModelLoaderTest.class.getResourceAsStream(name)) {
                return new YamlBEObjectModelLoader().load(in);
            }
        } catch (IOException e) {
            throw new RuntimeException("Unexpected I/O exception", e);
        }
    }

    private Object readTorrent(String name) {
        try (BEParser parser = new BEParser(YamlBEObjectModelLoaderTest.class.getResource(name))) {
            return parser.readMap();
        }
    }

    @Test
    public void testValidateTorrent() {

        Object torrentObject = readTorrent("single_file_correct.torrent");
        assertValidationSuccess(model.validate(torrentObject));
    }

    @Test
    public void testValidateTorrent_SingleFile_v2() {

        Object torrentObject = readTorrent("single_file_v2_correct.torrent");
        assertValidationSuccess(model_v2.validate(torrentObject));
    }

    @Test
    public void testValidateTorrent_MissingProperty() {

        Object torrentObject = readTorrent("single_file_missing_property.torrent");
        assertValidationFailure(model.validate(torrentObject), "properties are required: [piece length, pieces]");
    }

    @Test
    public void testValidateTorrent_InvalidType() {

        Object torrentObject = readTorrent("single_file_invalid_type.torrent");
        assertValidationFailure(model.validate(torrentObject), "Wrong type -- expected java.math.BigInteger");
    }

    @Test
    public void testValidateTorrent_MutuallyExclusiveProperties() {

        Object torrentObject = readTorrent("single_file_exclusive_properties.torrent");
        assertValidationFailure(model.validate(torrentObject), "properties are mutually exclusive: [[length], [files]]");
    }

    private static void assertValidationSuccess(ValidationResult validationResult) {
        assertTrue(validationResult.isSuccess());
        assertTrue(validationResult.getMessages().isEmpty());
    }

    private static void assertValidationFailure(ValidationResult validationResult, String... expectedMessages) {

        assertFalse(validationResult.isSuccess());

        for (String expectedMessage : expectedMessages) {
            for (String actualMessage : validationResult.getMessages()) {
                if (actualMessage.contains(expectedMessage)) {
                    continue;
                }
                throw new RuntimeException("Validation result does not contain expected message: " + expectedMessage);
            }
        }
    }
}
