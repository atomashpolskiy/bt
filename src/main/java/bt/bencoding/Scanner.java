package bt.bencoding;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.io.StringReader;
import java.nio.charset.Charset;

class Scanner {

    private final PushbackReader reader;

    Scanner(String encodedStr) {
        this.reader = new PushbackReader(new StringReader(encodedStr));
    }

    Scanner(InputStream in, Charset charset) {
        this.reader = new PushbackReader(new InputStreamReader(in, charset));
    }

    <T> T readObject(BEObjectBuilder<T> builder) throws Exception {

        int c;
        while ((c = reader.read()) != -1 && builder.accept((char) c))
            ;
        return builder.build();
    }

    char peek() {
        try {
            char c = (char) reader.read();
            reader.unread(c);
            return c;
        } catch (IOException e) {
            throw new RuntimeException("Unexpected I/O exception", e);
        }
    }
}
