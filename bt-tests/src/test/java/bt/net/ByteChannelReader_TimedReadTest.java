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

public class ByteChannelReader_TimedReadTest {

    private static final Duration waitBetweenReads = Duration.ofMillis(0);

    @Test
    public void testReader_timedRead_ExactAmount_SingleBlock() throws IOException {
        ReadByBlockChannel channel = createChannelWithTestData();
        int[] limits = new int[] {10, 20, 30, 40, 50};
        LimitingChannel limitedChannel = new LimitingChannel(channel, limits);
        ByteChannelReader reader = new ByteChannelReader(limitedChannel, Duration.ofSeconds(1), waitBetweenReads);
        ByteBuffer buf = ByteBuffer.allocate(100);

        int read = reader.timedRead(buf, 50, 50);
        assertEquals(50, read);
        assertEquals(50, buf.position());

        byte[] expected = new byte[50];
        byte[] actual = new byte[50];
        buf.get(actual);
        assertArrayEquals(expected, actual);
    }

    @Test
    public void testReader_timedRead_ExactAmount_MultipleBlocks() throws IOException {
        ReadByBlockChannel channel = createChannelWithTestData();
        int[] limits = new int[] {10, 20, 100, 125, 160, 160, 230, 230, 300};
        LimitingChannel limitedChannel = new LimitingChannel(channel, limits);
        ByteChannelReader reader = new ByteChannelReader(limitedChannel, Duration.ofSeconds(1), waitBetweenReads);
        ByteBuffer buf = ByteBuffer.allocate(300);

        int read = reader.timedRead(buf, 300, 300);
        assertEquals(300, read);
        assertEquals(buf.limit(), buf.position());

        byte[] expected = new byte[300];
        Arrays.fill(expected, 100, 200, (byte) 1);
        Arrays.fill(expected, 200, 300, (byte) 2);

        byte[] actual = new byte[300];
        buf.flip();
        buf.get(actual);
        assertArrayEquals(expected, actual);
    }

    @Test
    public void testReader_timedRead_InsufficientDataRead() throws IOException {
        ReadByBlockChannel channel = createChannelWithTestData();
        int[] limits = new int[] {10, 20, 30, 40, 50};
        LimitingChannel limitedChannel = new LimitingChannel(channel, limits);
        ByteChannelReader reader = new ByteChannelReader(limitedChannel, Duration.ofMillis(100), waitBetweenReads);
        ByteBuffer buf = ByteBuffer.allocate(100);

        assertExceptionWithMessage(it -> {
            try {
                return reader.timedRead(buf, 60, 60);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, "Less than 60 bytes received: 50");
    }

    @Test
    public void testReader_timedRead_ExcessiveDataRead() throws IOException {
        ReadByBlockChannel channel = createChannelWithTestData();
        int[] limits = new int[] {10, 20, 40};
        LimitingChannel limitedChannel = new LimitingChannel(channel, limits);
        ByteChannelReader reader = new ByteChannelReader(limitedChannel, Duration.ofMillis(100), waitBetweenReads);
        ByteBuffer buf = ByteBuffer.allocate(100);

        assertExceptionWithMessage(it -> {
            try {
                return reader.timedRead(buf, 30, 30);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, "More than 30 bytes received: 40");
    }

    @Test
    public void testReader_timedRead_EOF() throws IOException {
        ReadByBlockChannel channel = createChannelWithTestData();
        int[] limits = new int[] {10, 20, 30, 40, 50};
        LimitingChannel limitedChannel = LimitingChannel.withEOF(channel, limits);
        ByteChannelReader reader = new ByteChannelReader(limitedChannel, Duration.ofMillis(100), waitBetweenReads);
        ByteBuffer buf = ByteBuffer.allocate(100);

        assertExceptionWithMessage(it -> {
            try {
                return reader.timedRead(buf, 60, 60);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, "Received EOF, total bytes read: 50, expected: 60..60");
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
