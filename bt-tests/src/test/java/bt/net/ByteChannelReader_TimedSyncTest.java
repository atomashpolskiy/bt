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

package bt.net;

import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static bt.TestUtil.assertExceptionWithMessage;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class ByteChannelReader_TimedSyncTest {

    private static final Duration receiveTimeout = Duration.ofMillis(10);
    private static final Duration waitBetweenReads = Duration.ofMillis(0);

    /*************************************/
    /******** Generic read tests *********/
    /*************************************/

    @Test
    public void testReader_timedSync_NoMatch_ExactAmount_SingleBlock() throws IOException {
        ReadByBlockChannel channel = createChannelWithTestData();
        int[] limits = new int[] {10, 20, 30, 40, 50};
        LimitingChannel limitedChannel = new LimitingChannel(channel, limits);
        ByteChannelReader reader = testReader(limitedChannel);
        ByteBuffer buf = ByteBuffer.allocate(100);
        byte[] pattern = new byte[]{-1};

        assertExceptionWithMessage(it -> {
            try {
                return reader.readExactly(50).sync(buf, pattern);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, "Failed to synchronize: expected 50..50, received 50");
        assertEquals(50, buf.position());
    }

    @Test
    public void testReader_timedSync_NoMatch_ExactAmount_Unlimited() throws IOException {
        byte[] part0 = new byte[100];
        byte[] part1 = new byte[100];
        byte[] part2 = new byte[100];
        byte[] part3 = new byte[]{-1};

        List<byte[]> data = new ArrayList<>(Arrays.asList(part0, part1, part2, part3));
        ReadByBlockChannel channel = new ReadByBlockChannel(data);
        int[] limits = new int[] {300, 300, 300, 301};
        LimitingChannel limitedChannel = new LimitingChannel(channel, limits);
        ByteChannelReader reader = testReader(limitedChannel);
        ByteBuffer buf = ByteBuffer.allocate(301);
        byte[] pattern = new byte[]{-1};

        int read = reader.readExactly(301).sync(buf, pattern);
        assertEquals(301, read);
        assertEquals(buf.limit(), buf.position());
    }

    @Test
    public void testReader_timedSync_NoMatch_ExactAmount_MultipleBlocks() throws IOException {
        byte[] part0 = new byte[100];
        byte[] part1 = new byte[100];
        byte[] part2 = new byte[100];
        byte[] part3 = new byte[]{-1};

        Arrays.fill(part1, (byte) 1);
        Arrays.fill(part2, (byte) 2);

        List<byte[]> data = new ArrayList<>(Arrays.asList(part0, part1, part2, part3));
        ReadByBlockChannel channel = new ReadByBlockChannel(data);
        int[] limits = new int[] {10, 20, 100, 125, 160, 160, 230, 230, 300, 301};
        LimitingChannel limitedChannel = new LimitingChannel(channel, limits);
        ByteChannelReader reader = testReader(limitedChannel);
        ByteBuffer buf = ByteBuffer.allocate(301);

        int read = reader.readExactly(301).sync(buf, part3);
        assertEquals(301, read);
        assertEquals(buf.limit(), buf.position());

        byte[] expected = new byte[301];
        Arrays.fill(expected, 100, 200, (byte) 1);
        Arrays.fill(expected, 200, 300, (byte) 2);
        expected[expected.length - 1] = -1;

        byte[] actual = new byte[301];
        buf.flip();
        buf.get(actual);
        assertArrayEquals(expected, actual);
    }

    @Test
    public void testReader_timedSync_NoMatch_InsufficientDataRead() throws IOException {
        ReadByBlockChannel channel = createChannelWithTestData();
        int[] limits = new int[] {10, 20, 30, 40, 50};
        LimitingChannel limitedChannel = new LimitingChannel(channel, limits);
        ByteChannelReader reader = testReader(limitedChannel);
        ByteBuffer buf = ByteBuffer.allocate(100);
        byte[] pattern = new byte[]{-1};

        assertExceptionWithMessage(it -> {
            try {
                return reader.readExactly(60).sync(buf, pattern);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, "Less than 60 bytes received: 50");
    }

    @Test
    public void testReader_timedSync_NoMatch_ExcessiveDataRead() throws IOException {
        ReadByBlockChannel channel = createChannelWithTestData();
        int[] limits = new int[] {10, 20, 40};
        LimitingChannel limitedChannel = new LimitingChannel(channel, limits);
        ByteChannelReader reader = testReader(limitedChannel);
        ByteBuffer buf = ByteBuffer.allocate(100);
        byte[] pattern = new byte[]{-1};

        assertExceptionWithMessage(it -> {
            try {
                return reader.readExactly(30).sync(buf, pattern);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, "More than 30 bytes received: 40");
    }

    @Test
    public void testReader_timedSync_NoMatch_EOF() throws IOException {
        ReadByBlockChannel channel = createChannelWithTestData();
        int[] limits = new int[] {10, 20, 30, 40, 50};
        LimitingChannel limitedChannel = LimitingChannel.withEOF(channel, limits);
        ByteChannelReader reader = testReader(limitedChannel);
        ByteBuffer buf = ByteBuffer.allocate(100);
        byte[] pattern = new byte[]{-1};

        assertExceptionWithMessage(it -> {
            try {
                return reader.readExactly(60).sync(buf, pattern);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, "Received EOF, total bytes read: 50, expected: 60..60");
    }

    /****************************************/
    /******** Synchronization tests *********/
    /****************************************/

    /********** Single-byte pattern **********/

    @Test
    public void testReader_timedSync_Match_SingleBytePattern_BeginningOfStream() throws IOException {
        ReadByBlockChannel channel = createChannelWithTestData();
        int[] limits = new int[] {10, 20};
        LimitingChannel limitedChannel = new LimitingChannel(channel, limits);
        ByteChannelReader reader = testReader(limitedChannel);
        ByteBuffer buf = ByteBuffer.allocate(100);
        byte[] pattern = new byte[]{0};

        int read = reader.readNoMoreThan(20).sync(buf, pattern);
        assertEquals(20, read);
        assertEquals(1, buf.position());
    }

    @Test
    public void testReader_timedSync_Match_SingleBytePattern_MiddleOfStream() throws IOException {
        ReadByBlockChannel channel = createChannelWithTestData();
        int[] limits = new int[] {10, 20, 75, 125, 125};
        LimitingChannel limitedChannel = new LimitingChannel(channel, limits);
        ByteChannelReader reader = testReader(limitedChannel);
        ByteBuffer buf = ByteBuffer.allocate(300);
        byte[] pattern = new byte[]{1};

        int read = reader.readNoMoreThan(125).sync(buf, pattern);
        assertEquals(125, read);
        assertEquals(101, buf.position());
    }

    @Test
    public void testReader_timedSync_Match_SingleBytePattern_EndOfStream() throws IOException {
        byte[] part0 = new byte[100];
        byte[] part1 = new byte[100];
        byte[] part2 = new byte[1];

        Arrays.fill(part1, (byte) 1);
        Arrays.fill(part2, (byte) 2);

        List<byte[]> data = new ArrayList<>(Arrays.asList(part0, part1, part2));
        ReadByBlockChannel channel = new ReadByBlockChannel(data);
        int[] limits = new int[] {10, 20, 75, 125, 200, 201};
        LimitingChannel limitedChannel = new LimitingChannel(channel, limits);
        ByteChannelReader reader = testReader(limitedChannel);
        ByteBuffer buf = ByteBuffer.allocate(300);
        byte[] pattern = new byte[]{2};

        int read = reader.readNoMoreThan(201).sync(buf, pattern);
        assertEquals(201, read);
        assertEquals(201, buf.position());
    }

    /********** Multi-byte pattern **********/

    @Test
    public void testReader_timedSync_Match_MultiBytePattern_ShorterThanReadBlock_BeginningOfStream() throws IOException {
        ReadByBlockChannel channel = createChannelWithTestData();
        int[] limits = new int[] {10};
        LimitingChannel limitedChannel = new LimitingChannel(channel, limits);
        ByteChannelReader reader = testReader(limitedChannel);
        ByteBuffer buf = ByteBuffer.allocate(100);
        byte[] pattern = new byte[]{0,0,0,0,0};

        int read = reader.readNoMoreThan(10).sync(buf, pattern);
        assertEquals(10, read);
        assertEquals(pattern.length, buf.position());
    }

    @Test
    public void testReader_timedSync_Match_MultiBytePattern_ShorterThanReadBlock_MiddleOfStream() throws IOException {
        ReadByBlockChannel channel = createChannelWithTestData();
        int[] limits = new int[] {100, 200};
        LimitingChannel limitedChannel = new LimitingChannel(channel, limits);
        ByteChannelReader reader = testReader(limitedChannel);
        ByteBuffer buf = ByteBuffer.allocate(300);
        byte[] pattern = new byte[]{1,1,1,1,1};

        int read = reader.readNoMoreThan(200).sync(buf, pattern);
        assertEquals(200, read);
        assertEquals(100 + pattern.length, buf.position());
    }

    @Test
    public void testReader_timedSync_Match_MultiBytePattern_ShorterThanReadBlock_EndOfStream() throws IOException {
        byte[] part0 = new byte[100];
        byte[] part1 = new byte[100];
        byte[] part2 = new byte[10];

        Arrays.fill(part1, (byte) 1);
        Arrays.fill(part2, (byte) 2);

        ReadByBlockChannel channel = new ReadByBlockChannel(Arrays.asList(part0, part1, part2));
        int[] limits = new int[] {100, 200, 210};
        LimitingChannel limitedChannel = new LimitingChannel(channel, limits);
        ByteChannelReader reader = testReader(limitedChannel);
        ByteBuffer buf = ByteBuffer.allocate(300);
        byte[] pattern = new byte[]{2,2,2,2,2};

        int read = reader.readNoMoreThan(210).sync(buf, pattern);
        assertEquals(210, read);
        assertEquals(200 + pattern.length, buf.position());
    }

    @Test
    public void testReader_timedSync_Match_MultiBytePattern_LongerThanReadBlock_BeginningOfStream() throws IOException {
        ReadByBlockChannel channel = createChannelWithTestData();
        int[] limits = new int[] {10, 20, 30};
        LimitingChannel limitedChannel = new LimitingChannel(channel, limits);
        ByteChannelReader reader = testReader(limitedChannel);
        ByteBuffer buf = ByteBuffer.allocate(100);
        byte[] pattern = new byte[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};

        int read = reader.readNoMoreThan(30).sync(buf, pattern);
        assertEquals(30, read);
        assertEquals(pattern.length, buf.position());
    }

    @Test
    public void testReader_timedSync_Match_MultiBytePattern_LongerThanReadBlock_MiddleOfStream() throws IOException {
        ReadByBlockChannel channel = createChannelWithTestData();
        int[] limits = new int[] {90, 100, 120, 130};
        LimitingChannel limitedChannel = new LimitingChannel(channel, limits);
        ByteChannelReader reader = testReader(limitedChannel);
        ByteBuffer buf = ByteBuffer.allocate(200);
        byte[] pattern = new byte[]{1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,};

        int read = reader.readNoMoreThan(130).sync(buf, pattern);
        assertEquals(130, read);
        assertEquals(100 + pattern.length, buf.position());
    }

    @Test
    public void testReader_timedSync_Match_MultiBytePattern_LongerThanReadBlock_EndOfStream() throws IOException {
        byte[] part0 = new byte[100];
        byte[] part1 = new byte[100];
        byte[] part2 = new byte[30];

        Arrays.fill(part1, (byte) 1);
        Arrays.fill(part2, (byte) 2);

        ReadByBlockChannel channel = new ReadByBlockChannel(Arrays.asList(part0, part1, part2));
        int[] limits = new int[] {100, 200, 210, 220, 230};
        LimitingChannel limitedChannel = new LimitingChannel(channel, limits);
        ByteChannelReader reader = testReader(limitedChannel);
        ByteBuffer buf = ByteBuffer.allocate(300);
        byte[] pattern = new byte[]{2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,};

        int read = reader.readNoMoreThan(230).sync(buf, pattern);
        assertEquals(230, read);
        assertEquals(200 + pattern.length, buf.position());
    }

    private static ReadByBlockChannel createChannelWithTestData() {
        byte[] part0 = new byte[100];
        byte[] part1 = new byte[100];
        byte[] part2 = new byte[100];

        Arrays.fill(part1, (byte) 1);
        Arrays.fill(part2, (byte) 2);

        List<byte[]> data = new ArrayList<>(Arrays.asList(part0, part1, part2));
        return new ReadByBlockChannel(data);
    }
    
    private static ByteChannelReader testReader(ReadableByteChannel channel) {
        return ByteChannelReader.forChannel(channel).withTimeout(receiveTimeout).waitBetweenReads(waitBetweenReads);
    }
}
