/*
 * Copyright (c) 2016—2021 Andrei Tomashpolskiy and individual contributors.
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

package bt.bencoding.serializers;

import bt.bencoding.BEType;
import bt.bencoding.model.BEObject;
import bt.bencoding.types.BEMap;

import java.util.HashMap;
import java.util.Map;

class BEMapDecoder extends BEPrefixedTypeDecoder<BEMap> {
    private final Map<String, BEObject<?>> map;
    private BEStringDecoder keyBuilder;
    private BEObjectDecoder<? extends BEObject<?>> valueBuilder;

    BEMapDecoder() {
        map = new HashMap<>();
    }

    @Override
    protected boolean doAccept(int b) {

        if (keyBuilder == null) {
            keyBuilder = new BEStringDecoder();
        }
        if (valueBuilder == null) {
            if (!keyBuilder.accept(b)) {
                BEType valueType = BEParser.getTypeForPrefix((char) b);
                valueBuilder = BEParser.builderForType(valueType);
                return valueBuilder.accept(b);
            }
        } else {
            if (!valueBuilder.accept(b)) {
                map.put(keyBuilder.build().getValueAsString(), valueBuilder.build());
                keyBuilder = null;
                valueBuilder = null;
                return accept(b, false);
            }
        }
        return true;
    }

    @Override
    protected BEMap doBuild(byte[] content) {
        return new BEMap(content, map);
    }

    @Override
    protected boolean acceptEOF() {
        return keyBuilder == null && valueBuilder == null;
    }

    @Override
    public BEType getType() {
        return BEType.MAP;
    }
}
