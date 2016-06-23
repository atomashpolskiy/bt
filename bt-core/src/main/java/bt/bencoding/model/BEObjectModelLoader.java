package bt.bencoding.model;

import java.io.InputStream;

public interface BEObjectModelLoader {

    BEObjectModel load(InputStream source);
}
