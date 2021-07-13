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

package bt.bencoding.serializers;

import bt.bencoding.BEType;
import bt.bencoding.types.BEInteger;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

class BEIntegerDecoder extends BEPrefixedTypeDecoder<BEInteger> {
    private enum State {
        EXPECT_DIGIT_OR_MINUS, // haven't read anything
        EXPECT_NON_ZERO_DIGIT, // Read '-', so a zero after is invalid
        EXPECT_DIGIT_OR_END,   // Read partial number. Number may be done, or more digits
        DONE, // Number fully read. Either after a beginning zero, or number was built.
        ERROR; // Invalid state, irrecoverable.
    }

    private State currState = State.EXPECT_DIGIT_OR_MINUS;

    BEIntegerDecoder() {
    }

    @Override
    protected boolean doAccept(int b) {
        char c = (char) b;
        switch (currState) {
            case EXPECT_DIGIT_OR_MINUS:
                switch (c) {
                    case '0':
                        currState = State.DONE;
                        break;
                    case '-':
                        currState = State.EXPECT_NON_ZERO_DIGIT;
                        break;
                    default:
                        ensureDigit(c);
                        currState = State.EXPECT_DIGIT_OR_END;
                        break;
                }
                break;
            case EXPECT_NON_ZERO_DIGIT:
                if (c == '0') {
                    throw illegalCharacterRead(c);
                }
            case EXPECT_DIGIT_OR_END:
                ensureDigit(c);
                currState = State.EXPECT_DIGIT_OR_END;
                break;
            case DONE:
                throw illegalCharacterRead(c);
        }
        return true;
    }

    private void ensureDigit(char c) {
        if (!BEParser.IS_DIGIT.matches(c)) {
            throw illegalCharacterRead(c);
        }
    }

    private IllegalArgumentException illegalCharacterRead(char c) {
        currState = State.ERROR;
        return new IllegalArgumentException("Unexpected token while reading integer (as ASCII char): " + c);
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
        if (currState != State.DONE) {
            if (currState == State.EXPECT_DIGIT_OR_END) {
                currState = State.DONE;
            } else {
                throw new IllegalStateException(
                        "Partially read integer: " + new String(content, StandardCharsets.US_ASCII));
            }
        }
        // digits should be strictly ascii - no need to worry about utf. Chop off head byte which is 'i',
        // and last byte which is 'e'
        final String val = new String(content, 1, content.length - 2, StandardCharsets.US_ASCII);

        Number num = Ints.tryParse(val);
        if (num == null) {
            num = Longs.tryParse(val);
        }
        if (num == null) {
            num = new BigInteger(val);
        }
        return new BEInteger(content, num);
    }
}
