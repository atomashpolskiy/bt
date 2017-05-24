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
        if (e.getMessage() == null || !e.getMessage().contains(message)) {
            throw new RuntimeException("Expected string containing text: '" + message +
                    "', but actual was: '" + e.getMessage() + "'", e);
        }
    }
}
