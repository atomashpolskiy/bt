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

package bt.bencoding;

import bt.bencoding.model.BEList;
import bt.bencoding.model.BEObject;

import java.util.ArrayList;
import java.util.List;

class BEListBuilder extends BEPrefixedTypeBuilder<BEList> {

    private final List<BEObject<?>> objects;
    private BEObjectBuilder<? extends BEObject<?>> builder;

    BEListBuilder() {
        objects = new ArrayList<>();
    }

    @Override
    protected boolean doAccept(int b) {

        if (builder == null) {
            BEType type = BEParser.getTypeForPrefix((char) b);
            builder = BEParser.builderForType(type);
        }
        if (!builder.accept(b)) {
            objects.add(builder.build());
            builder = null;
            return accept(b, false);
        }
        return true;
    }

    @Override
    public boolean acceptEOF() {
        return builder == null;
    }

    @Override
    protected BEList doBuild(byte[] content) {
        return new BEList(content, objects);
    }

    @Override
    public BEType getType() {
        return BEType.LIST;
    }
}
