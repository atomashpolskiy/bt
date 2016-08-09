package bt.tracker;

import java.io.OutputStream;

public interface SecretKey {

    void writeTo(OutputStream out);
}
