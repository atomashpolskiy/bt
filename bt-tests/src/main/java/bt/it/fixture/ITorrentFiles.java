package bt.it.fixture;

import java.io.File;
import java.net.URL;

public interface ITorrentFiles {
    URL getMetainfoUrl();

    void createFiles(File root);

    void createRoot(File root);

    boolean verifyFiles(File root);
}
