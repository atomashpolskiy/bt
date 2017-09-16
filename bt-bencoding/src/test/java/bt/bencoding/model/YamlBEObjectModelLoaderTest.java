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

import bt.bencoding.BEParser;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class YamlBEObjectModelLoaderTest {

    BEObjectModel model;

    @Before
    public void setUp() {
        model = loadModel("metainfo_correct.yml");
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
