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

import bt.bencoding.model.BEInteger;

import java.math.BigInteger;

class BEIntegerBuilder extends BEPrefixedTypeBuilder<BEInteger> {

    private StringBuilder buf;

    BEIntegerBuilder() {
        buf = new StringBuilder();
    }

    @Override
    protected boolean doAccept(int b) {

        char c = (char) b;
        if (Character.isDigit(c) || buf.length() == 0 && c == '-') {
            buf.append(c);
            return true;
        }
        throw new IllegalArgumentException("Unexpected token while reading integer (as ASCII char): " + c);
    }

    @Override
    protected boolean acceptEOF() {
        return true;
    }

    @Override
    public BEType getType() {
        return BEType.INTEGER;
    }

    @Override
    protected BEInteger doBuild(byte[] content) {
        return new BEInteger(content, new BigInteger(buf.toString()));
    }
}
