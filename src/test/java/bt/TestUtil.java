package bt;

import java.util.function.Function;

import static org.junit.Assert.assertNotNull;

public class TestUtil {

    public static void assertExceptionWithMessage(Function<?,?> function, String message) {

        if (message == null || message.isEmpty()) {
            throw new RuntimeException("Empty message");
        }

        Exception e = null;
        try {
            function.apply(null);
        } catch (Exception e1) {
            e = e1;
        }

        assertNotNull(e);
        assertContainsText(e.getMessage(), message);
    }

    public static void assertContainsText(String s, String text) {
        if (!s.contains(text)) {
            throw new RuntimeException("Expected string containing text: '" + text + "', but actual was: '" + s + "'");
        }
    }
}
