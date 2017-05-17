package bt.net;

import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static bt.TestUtil.assertExceptionWithMessage;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class ByteChannelReader_TimedSyncTest {

    private static final Duration waitBetweenReads = Duration.ofMillis(0);

    /*************************************/
    /******** Generic read tests *********/
    /*************************************/

    @Test
    public void testReader_timedSync_NoMatch_ExactAmount_SingleBlock() throws IOException {
        ReadByBlockChannel channel = createChannelWithTestData();
        int[] limits = new int[] {10, 20, 30, 40, 50};
        LimitingChannel limitedChannel = new LimitingChannel(channel, limits);
        ByteChannelReader reader = new ByteChannelReader(limitedChannel, Duration.ofSeconds(1), waitBetweenReads);
        ByteBuffer buf = ByteBuffer.allocate(100);
        byte[] pattern = new byte[]{-1};

        assertExceptionWithMessage(it -> {
            try {
                return reader.timedSync(buf, 50, 50, pattern);
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
        ByteChannelReader reader = new ByteChannelReader(limitedChannel, Duration.ofSeconds(1), waitBetweenReads);
        ByteBuffer buf = ByteBuffer.allocate(301);
        byte[] pattern = new byte[]{-1};

        int read = reader.timedSync(buf, 301, 301, pattern);
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
        ByteChannelReader reader = new ByteChannelReader(limitedChannel, Duration.ofSeconds(1), waitBetweenReads);
        ByteBuffer buf = ByteBuffer.allocate(301);

        int read = reader.timedSync(buf, 301, 301, part3);
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
        ByteChannelReader reader = new ByteChannelReader(limitedChannel, Duration.ofMillis(100), waitBetweenReads);
        ByteBuffer buf = ByteBuffer.allocate(100);
        byte[] pattern = new byte[]{-1};

        assertExceptionWithMessage(it -> {
            try {
                return reader.timedSync(buf, 60, 60, pattern);
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
        ByteChannelReader reader = new ByteChannelReader(limitedChannel, Duration.ofMillis(100), waitBetweenReads);
        ByteBuffer buf = ByteBuffer.allocate(100);
        byte[] pattern = new byte[]{-1};

        assertExceptionWithMessage(it -> {
            try {
                return reader.timedSync(buf, 30, 30, pattern);
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
        ByteChannelReader reader = new ByteChannelReader(limitedChannel, Duration.ofMillis(100), waitBetweenReads);
        ByteBuffer buf = ByteBuffer.allocate(100);
        byte[] pattern = new byte[]{-1};

        assertExceptionWithMessage(it -> {
            try {
                return reader.timedSync(buf, 60, 60, pattern);
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
        int[] limits = new int[] {10, 20, 30, 40, 50};
        LimitingChannel limitedChannel = new LimitingChannel(channel, limits);
        ByteChannelReader reader = new ByteChannelReader(limitedChannel, Duration.ofSeconds(1), waitBetweenReads);
        ByteBuffer buf = ByteBuffer.allocate(100);
        byte[] pattern = new byte[]{0};

        int read = reader.timedSync(buf, 0, 20, pattern);
        assertEquals(10, read);
        assertEquals(1, buf.position());
    }

    @Test
    public void testReader_timedSync_Match_SingleBytePattern_MiddleOfStream() throws IOException {
        ReadByBlockChannel channel = createChannelWithTestData();
        int[] limits = new int[] {10, 20, 75, 125, 125};
        LimitingChannel limitedChannel = new LimitingChannel(channel, limits);
        ByteChannelReader reader = new ByteChannelReader(limitedChannel, Duration.ofSeconds(1), waitBetweenReads);
        ByteBuffer buf = ByteBuffer.allocate(300);
        byte[] pattern = new byte[]{1};

        int read = reader.timedSync(buf, 0, 125, pattern);
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
        int[] limits = new int[] {10, 20, 75, 125, 250, 250, 300};
        LimitingChannel limitedChannel = new LimitingChannel(channel, limits);
        ByteChannelReader reader = new ByteChannelReader(limitedChannel, Duration.ofSeconds(1), waitBetweenReads);
        ByteBuffer buf = ByteBuffer.allocate(300);
        byte[] pattern = new byte[]{2};

        int read = reader.timedSync(buf, 0, 250, pattern);
        assertEquals(201, read);
        assertEquals(201, buf.position());
    }

    /********** Multi-byte pattern **********/

    @Test
    public void testReader_timedSync_Match_MultiBytePattern_ShorterThanReadBlock_BeginningOfStream() throws IOException {
        ReadByBlockChannel channel = createChannelWithTestData();
        int[] limits = new int[] {10, 20, 30, 40, 50};
        LimitingChannel limitedChannel = new LimitingChannel(channel, limits);
        ByteChannelReader reader = new ByteChannelReader(limitedChannel, Duration.ofSeconds(1), waitBetweenReads);
        ByteBuffer buf = ByteBuffer.allocate(100);
        byte[] pattern = new byte[]{0,0,0,0,0};

        int read = reader.timedSync(buf, 0, 10, pattern);
        assertEquals(10, read);
        assertEquals(pattern.length, buf.position());
    }

    @Test
    public void testReader_timedSync_Match_MultiBytePattern_ShorterThanReadBlock_MiddleOfStream() throws IOException {
        ReadByBlockChannel channel = createChannelWithTestData();
        int[] limits = new int[] {100, 200, 300};
        LimitingChannel limitedChannel = new LimitingChannel(channel, limits);
        ByteChannelReader reader = new ByteChannelReader(limitedChannel, Duration.ofSeconds(1), waitBetweenReads);
        ByteBuffer buf = ByteBuffer.allocate(300);
        byte[] pattern = new byte[]{1,1,1,1,1};

        int read = reader.timedSync(buf, 0, 200, pattern);
        assertEquals(200, read);
        assertEquals(100 + pattern.length, buf.position());
    }

    @Test
    public void testReader_timedSync_Match_MultiBytePattern_LongerThanReadBlock_BeginningOfStream() throws IOException {
        ReadByBlockChannel channel = createChannelWithTestData();
        int[] limits = new int[] {10, 20, 30, 40, 50};
        LimitingChannel limitedChannel = new LimitingChannel(channel, limits);
        ByteChannelReader reader = new ByteChannelReader(limitedChannel, Duration.ofSeconds(100), waitBetweenReads);
        ByteBuffer buf = ByteBuffer.allocate(100);
        byte[] pattern = new byte[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};

        int read = reader.timedSync(buf, 0, 30, pattern);
        assertEquals(30, read);
        assertEquals(pattern.length, buf.position());
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
}
