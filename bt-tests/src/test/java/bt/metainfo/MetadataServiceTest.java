package bt.metainfo;

import bt.bencoding.BtParseException;
import bt.tracker.AnnounceKey;
import org.junit.Before;
import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class MetadataServiceTest {

    private IMetadataService metadataService;

    @Before
    public void setUp() {
        metadataService = new MetadataService();
    }

    @Test
    public void testBuildTorrent_SingleFile() throws Exception {

        Torrent torrent = metadataService.fromUrl(MetadataServiceTest.class.getResource("single_file.torrent"));

        assertHasAttributes(torrent,
                new AnnounceKey(new URL("http://bt2.t-ru.org/ann")), "3du-Arch-Uni-i686.iso", 524288L, 1766, 925892608L);

        assertNotNull(torrent.getFiles());
        assertEquals(1, torrent.getFiles().size());

        // TODO: add check for the torrent file
    }

    @Test
    public void testBuildTorrent_MultiFile() throws Exception {

        Torrent torrent = metadataService.fromUrl(MetadataServiceTest.class.getResource("multi_file.torrent"));

        AnnounceKey announceKey = new AnnounceKey(Arrays.asList(
                Collections.singletonList(new URL("http://bt3.t-ru.org/ann")),
                Collections.singletonList(new URL("http://retracker.local/announce"))
        ));
        assertHasAttributes(torrent, announceKey, "VMWARE_ARCH", 4194304L, 1329, 0L);

        assertNotNull(torrent.getFiles());
        assertEquals(6, torrent.getFiles().size());

        // TODO: add checks for all torrent files
    }

    private void assertHasAttributes(Torrent torrent, AnnounceKey announceKey, String name, long chunkSize,
                                     int chunkHashesCount, long size) throws MalformedURLException {

        assertEquals(announceKey, torrent.getAnnounceKey());
        assertEquals(name, torrent.getName());
        assertEquals(chunkSize, torrent.getChunkSize());

        int actualChunkHashesCount = 0;
        for (byte[] hash : torrent.getChunkHashes()) {
            assertEquals(20, hash.length);
            actualChunkHashesCount++;
        }
        assertEquals(chunkHashesCount, actualChunkHashesCount);

        assertEquals(size, torrent.getSize());
    }

    @Test
    public void testBuildTorrent_ParseExceptionContents() {

        String metainfo = "d8:announce15:http://t.co/ann####";
        byte[] bytes = metainfo.getBytes(Charset.forName("ASCII"));

        BtParseException exception = null;
        try {
            metadataService.fromByteArray(bytes);
        } catch (BtParseException e) {
            exception = e;
        }

        assertNotNull(exception);
        assertArrayEquals(Arrays.copyOfRange(bytes, 0, 30), exception.getScannedContents());
    }
}
