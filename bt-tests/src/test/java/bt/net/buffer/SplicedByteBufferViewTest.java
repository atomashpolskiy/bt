/*
 * Copyright (c) 2016—2019 Andrei Tomashpolskiy and individual contributors.
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

package bt.net.buffer;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class SplicedByteBufferViewTest {

    private ByteBuffer left;
    private ByteBuffer right;

    @Before
    public void setUp() {
        byte[] bytesLeft = "I am a little pony and I do funny tricks, when I am in the mood."
                .getBytes(StandardCharsets.UTF_8);
        byte[] bytesRight = "...If you scratch my back, I'll scratch yours."
                .getBytes(StandardCharsets.UTF_8);

        this.left = ByteBuffer.wrap(bytesLeft);
        this.right = ByteBuffer.wrap(bytesRight);
    }

    @Test
    public void test() throws Exception {
        left.limit(left.limit() - "when I am in the mood.".length());
        left.position(left.limit() - "I do funny tricks, ".length());

        right.limit("...If you scratch my back".length());
        right.position("...".length());

        ByteBufferView sb = new SplicedByteBufferView(left, right)
                .get(new byte[8])
                .duplicate()
                .position(0);

        byte[] bytes1 = new byte[sb.remaining()];
        sb.get(bytes1);
        String s1 = new String(bytes1, StandardCharsets.UTF_8);
        assertEquals("I do funny tricks, If you scratch my back", s1);
        assertFalse(sb.hasRemaining());
        assertEquals(sb.position(), sb.limit());
        ////////////////////////////////////////

        sb.position(0);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while (sb.transferTo(Channels.newChannel(out)) > 0)
            ;
        String s2 = new String(out.toByteArray(), StandardCharsets.UTF_8);
        assertEquals("I do funny tricks, If you scratch my back", s2);
        assertFalse(sb.hasRemaining());
        assertEquals(sb.position(), sb.limit());
        ////////////////////////////////////////

        sb.position(0);
        ByteBuffer buf = ByteBuffer.allocate(64);
        sb.transferTo(buf);
        buf.flip();
        byte[] bytes3 = new byte[buf.remaining()];
        buf.get(bytes3);
        String s3 = new String(bytes3, StandardCharsets.UTF_8);
        assertEquals("I do funny tricks, If you scratch my back", s3);
        assertFalse(sb.hasRemaining());
        assertEquals(sb.position(), sb.limit());
        ////////////////////////////////////////
    }
}
