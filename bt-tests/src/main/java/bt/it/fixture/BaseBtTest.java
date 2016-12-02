package bt.it.fixture;

import org.junit.BeforeClass;
import org.junit.Rule;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

public class BaseBtTest {

    private static final File ROOT = new File("target/it");

    private static final String SINGLE_FILE_NAME = "file.txt";
    private static final URL SINGLE_METAINFO_URL = BaseBtTest.class.getResource(SINGLE_FILE_NAME + ".torrent");
    private static final URL SINGLE_FILE_URL = BaseBtTest.class.getResource(SINGLE_FILE_NAME);

    private static byte[] SINGLE_FILE_CONTENT;

    @BeforeClass
    public static void setUpClass() {
        try {
            File singleFile = new File(SINGLE_FILE_URL.toURI());
            byte[] content = new byte[(int) singleFile.length()];
            try (FileInputStream fin = new FileInputStream(singleFile)) {
                fin.read(content);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read file: " + singleFile.getPath(), e);
            }
            SINGLE_FILE_CONTENT = content;
        } catch (URISyntaxException e) {
            throw new RuntimeException("Unexpected error", e);
        }
    }

    @Rule
    public BtTestRuntimeFactory runtimeFactory = new BtTestRuntimeFactory();

    private Collection<BtTestRuntimeFeature> features;

    protected BaseBtTest() {
        this.features = Collections.emptyList();
    }

    protected BaseBtTest(Collection<BtTestRuntimeFeature> features) {
        this.features = Objects.requireNonNull(features);
    }

    protected Swarm.SwarmBuilder buildSwarm() {
        return Swarm.builder(new File(ROOT, this.getClass().getName()), runtimeFactory, features);
    }

    protected static TorrentFiles getSingleFile() {
        return DefaultTorrentFiles.builder(SINGLE_METAINFO_URL).file(SINGLE_FILE_NAME, SINGLE_FILE_CONTENT).build();
    }
}
