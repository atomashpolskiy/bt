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

package bt.protocol.handler;

import bt.protocol.DecodingContext;
import bt.protocol.EncodingContext;
import bt.protocol.Unchoke;

import java.nio.ByteBuffer;

import static bt.protocol.Protocols.verifyPayloadHasLength;

public final class UnchokeHandler extends UniqueMessageHandler<Unchoke> {

    public UnchokeHandler() {
        super(Unchoke.class);
    }

    @Override
    public int doDecode(DecodingContext context, ByteBuffer buffer) {
        verifyPayloadHasLength(Unchoke.class, 0, buffer.remaining());
        context.setMessage(Unchoke.instance());
        return 0;
    }

    @Override
    public boolean doEncode(EncodingContext context, Unchoke message, ByteBuffer buffer) {
        return true;
    }
}
