package bt.tracker;

import java.io.OutputStream;

/**
 * A secret binary sequence, used for interaction with HTTP trackers.
 *
 * @since 1.0
 */
public interface SecretKey {

    /**
     * Writes the secret key into the provided output stream.
     *
     * @since 1.0
     */
    void writeTo(OutputStream out);
}
