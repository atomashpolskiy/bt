package bt.metainfo;

import bt.bencoding.BtParseException;
import bt.torrent.TorrentRegistry;
import bt.tracker.AnnounceKey;
import org.junit.Before;
import org.junit.Test;

import java.net.MalformedURLException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class MetadataServiceTest {

    private IMetadataService metadataService;

    @Before
    public void setUp() {
        metadataService = new MetadataService(mock(TorrentRegistry.class));
    }

    @Test
    public void testBuildTorrent_SingleFile() throws Exception {

        Torrent torrent = metadataService.fromUrl(MetadataServiceTest.class.getResource("single_file.torrent"));

        assertHasAttributes(torrent,
                Optional.of(new AnnounceKey("http://jupiter.gx/ann")), "Arch-Uni-i686.iso", 524288L, 1766, 925892608L);

        assertNotNull(torrent.getFiles());
        assertEquals(1, torrent.getFiles().size());

        // TODO: add check for the torrent file
    }

    @Test
    public void testBuildTorrent_SingleFile_InfoDictionaryOnly() throws Exception {

        Torrent torrent = metadataService.fromUrl(MetadataServiceTest.class.getResource("single_file_info_dictionary.bin"));

        assertHasAttributes(torrent, Optional.empty(), "Arch-Uni-i686.iso", 524288L, 1766, 925892608L);

        assertNotNull(torrent.getFiles());
        assertEquals(1, torrent.getFiles().size());

        // TODO: add check for the torrent file
    }

    @Test
    public void testBuildTorrent_MultiFile() throws Exception {

        try {
            Torrent torrent = metadataService.fromUrl(MetadataServiceTest.class.getResource("multi_file.torrent"));

            AnnounceKey announceKey = new AnnounceKey(Arrays.asList(
                    Collections.singletonList("http://jupiter.gx/ann"),
                    Collections.singletonList("http://jupiter.local/announce")
            ));
            assertHasAttributes(torrent, Optional.of(announceKey), "BEWARE_BACH", 4194304L, 1329, 5573061611L);

            assertNotNull(torrent.getFiles());
            assertEquals(6, torrent.getFiles().size());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // TODO: add checks for all torrent files
    }

    private void assertHasAttributes(Torrent torrent,
                                     Optional<AnnounceKey> announceKey,
                                     String name,
                                     long chunkSize,
                                     int chunkHashesCount,
                                     long size) throws MalformedURLException {

        if (announceKey.isPresent()) {
            assertTrue(torrent.getAnnounceKey().isPresent());
            assertEquals(announceKey.get(), torrent.getAnnounceKey().get());
        }
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
