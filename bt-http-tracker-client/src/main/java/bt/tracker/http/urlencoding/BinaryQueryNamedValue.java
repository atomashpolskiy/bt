package bt.tracker.http.urlencoding;

import com.google.common.base.CharMatcher;
import com.google.common.primitives.Longs;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * A class that represents a parameter in the query string, along with it's decoded value. This class exists because
 * the tracker protocol requires encoding/decoding binary fields out of the query string, that are not part of any Charset
 */
public class BinaryQueryNamedValue {
    private static final CharMatcher SAFE_CHAR = CharMatcher.inRange('A', 'Z')
            .or(CharMatcher.inRange('a', 'z'))
            .or(CharMatcher.anyOf("-._~"));
    private final String name;
    private final byte[] value;

    /**
     * Create a new named value that is in a query string
     *
     * @param name  the name of the value
     * @param value the value
     */
    public BinaryQueryNamedValue(String name, byte[] value) {
        this.name = name;
        this.value = value;
        if (!SAFE_CHAR.matchesAllOf(name)) {
            throw new IllegalArgumentException("name must be safe: " + name);
        }
    }

    /**
     * Create a new named value that is in a query string. Will be sent as UTF-8.
     *
     * @param name  the name of the value
     * @param value the value
     */
    public BinaryQueryNamedValue(String name, String value) {
        this(name, value, StandardCharsets.UTF_8);
    }

    /**
     * Create a new named value that is in a query string
     *
     * @param name         the name of the value
     * @param value        the value
     * @param wireEncoding the charset to encode the value with when sent in a urlrequest
     */
    public BinaryQueryNamedValue(String name, String value, Charset wireEncoding) {
        this(name, value.getBytes(wireEncoding));
    }

    /**
     * Create a new named value that is in a query string. Will be sent as UTF-8.
     *
     * @param name  the name of the value
     * @param value the value
     */
    public BinaryQueryNamedValue(String name, long value) {
        this(name, Long.toString(value), StandardCharsets.US_ASCII);
    }

    /**
     * Get the name of this named value
     *
     * @return the name of this named value
     */
    public String getName() {
        return name;
    }

    /**
     * Get the value of this named value
     *
     * @return the value of this named value
     */
    public byte[] getValue() {
        return value;
    }

    /**
     * Get the value of this named value as a String decoded from UTF-8
     *
     * @return the value of this named value as a String decoded from UTF-8
     */
    public String getValueAsUtf8Str() {
        return new String(value, StandardCharsets.UTF_8);
    }

    /**
     * Get the value of this named value as a String decoded from the specified charset
     *
     * @return the value of this named value as a String decoded from the specified charset
     */
    public String getValue(Charset charset) {
        return new String(value, charset);
    }


    /**
     * Get the value of this named value as a long
     *
     * @return the value of this named value as a long
     * @throws IllegalArgumentException if this string isn't a long
     */
    public long getValueAsLong() {
        Long ret = Longs.tryParse(getValue(StandardCharsets.US_ASCII));
        if (ret == null)
            throw new IllegalStateException();
        return ret.longValue();
    }

    public String getUrlEncodedValue() {
        StringBuilder ret = new StringBuilder();

        for (byte b : value) {
            if (SAFE_CHAR.matches((char) b)) {
                ret.append((char) b);
            } else {
                ret.append('%');
                ret.append(lowerBitsToAscii(b >> 4));
                ret.append(lowerBitsToAscii(b));
            }
        }

        return ret.toString();
    }

    private char lowerBitsToAscii(int b) {
        int lowerFourBits = b & 0xF;
        if (lowerFourBits < 10)
            return (char) ('0' + lowerFourBits);
        return (char) ('A' + (lowerFourBits - 10));
    }
}
