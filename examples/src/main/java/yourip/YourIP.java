package yourip;

import bt.bencoding.model.BEMap;
import bt.bencoding.model.BEObject;
import bt.bencoding.model.BEString;
import bt.protocol.extended.ExtendedMessage;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.HashMap;

public class YourIP extends ExtendedMessage {

    private static final String id = "yourip";
    private static final String addressField = "address";

    public static String id() {
        return id;
    }

    public static String addressField() {
        return addressField;
    }

    private final String address;

    public YourIP(String address) {
        this.address = address;
    }

    public String getAddress() {
        return address;
    }

    void writeTo(OutputStream out) throws IOException {
        BEMap message = new BEMap(null, new HashMap<String, BEObject<?>>() {{
            put(addressField, new BEString(address.getBytes(Charset.forName("UTF-8"))));
        }});
        message.writeTo(out);
    }
}
