package bt.torrent.maker;

import bt.metainfo.MetadataService;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;
import bt.tracker.AnnounceKey;
import com.google.common.collect.Iterators;
import com.google.common.jimfs.Jimfs;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Simple tests for making a torrent
 */
public class TorrentMakerTest {
    private static final String CREATED_BY = "mrpyckle";
    private final String testfileName = "testfile.bin";
    private final String testfile2Name = "testfile2.bin";
    private final MetadataService metadataService = new MetadataService();
    private FileSystem tmpFs;
    private Path rootDir;

    @Before
    public void setUp() throws Exception {
        tmpFs = Jimfs.newFileSystem();
        rootDir = tmpFs.getPath("/test/root/for/testtorrent");
        Files.createDirectories(rootDir);
    }

    @After
    public void destroy() throws Exception {
        tmpFs.close();
        tmpFs = null;
    }

    /**
     * Test making a simple torrent with a single file
     *
     * @throws IOException on failure
     */
    @Test
    public void testSingleFileTorrent() throws IOException {
        TorrentBuilder torrentBuilder = makeTorrentBuilder(CREATED_BY, Collections.singletonList(testfileName));
        torrentBuilder.pieceSize(1 << 10);
        torrentBuilder.numHashingThreads(1);

        Torrent decodedTorrent = buildAndDecodeTorrent(torrentBuilder);
        Assert.assertEquals(testfileName, decodedTorrent.getName());
        Assert.assertEquals(CREATED_BY, decodedTorrent.getCreatedBy().get());
        Assert.assertEquals(torrentBuilder.getPieceSize(), decodedTorrent.getChunkSize());
        Assert.assertFalse(decodedTorrent.isPrivate());

        // should be automatically populated with current date if not specified
        Assert.assertTrue(decodedTorrent.getCreationDate().isPresent());
    }

    private TorrentBuilder makeTorrentBuilder(String createdBy, List<String> testFileNames) throws IOException {
        return makeTorrentBuilder(createdBy, testFileNames, 4);
    }

    private TorrentBuilder makeTorrentBuilder(String createdBy, List<String> testFileNames, int... fileLengths)
            throws IOException {
        TorrentBuilder torrentBuilder = new TorrentBuilder();

        int fileLenOffset = 0;
        for (String testfileName : testFileNames) {
            int fileLength = fileLengths[Math.min(fileLengths.length - 1, fileLenOffset++)];
            Path testFile = rootDir.resolve(testfileName);
            if (!Files.isDirectory(testFile)) {
                createDummyFile(fileLength, testFile);
            }
            torrentBuilder.addFile(testFile);
        }
        torrentBuilder.setRootPath(rootDir);

        torrentBuilder.createdBy(createdBy);
        return torrentBuilder;
    }

    private void createDummyFile(int fileLength, Path testFile) throws IOException {
        byte[] fileContents = getDummyByteContents(fileLength);
        Files.createDirectories(testFile.getParent());
        Files.write(testFile, fileContents);
    }

    private byte[] getDummyByteContents(int fileLength) {
        byte[] fileContents = new byte[fileLength];

        // fill contents with dummy contents
        for (int i = 0; i < fileLength; i++) {
            fileContents[i] = (byte) i;
        }
        return fileContents;
    }

    /**
     * Test making a simple torrent with multiple files
     *
     * @throws IOException on failure
     */
    @Test
    public void testMultiFileTorrent() throws IOException {
        TorrentBuilder torrentBuilder = makeTorrentBuilder(null, Arrays.asList(testfileName, testfile2Name));
        torrentBuilder.numHashingThreads(2);

        Torrent decodedTorrent = buildAndDecodeTorrent(torrentBuilder);
        Assert.assertEquals(testfileName, decodedTorrent.getFiles().get(0).getPathElements().get(0));
        Assert.assertEquals(testfile2Name, decodedTorrent.getFiles().get(1).getPathElements().get(0));
        Assert.assertFalse(decodedTorrent.getCreatedBy().isPresent());
        Assert.assertEquals(torrentBuilder.getPieceSize(), decodedTorrent.getChunkSize());
        Assert.assertFalse(decodedTorrent.isPrivate());
    }

    /**
     * Test making a simple torrent with multiple files in different folders and non normalized directory
     *
     * @throws IOException on failure
     */
    @Test
    public void testMultiFileTorrentWithDir() throws IOException {
        String file1 = "testfolder/testtesttest/./" + testfileName;
        String file2 = "./" + testfile2Name;
        TorrentBuilder torrentBuilder = makeTorrentBuilder(null, Arrays.asList(file1, file2));

        Torrent decodedTorrent = buildAndDecodeTorrent(torrentBuilder);
        TorrentFile torrentFile1 = decodedTorrent.getFiles().get(0);
        Path file1Path = tmpFs.getPath(file1);
        Assert.assertEquals(4, file1Path.getNameCount());
        file1Path = file1Path.normalize();
        Assert.assertEquals(3, file1Path.getNameCount());
        assertTorrentFileEqualsPath(torrentFile1, file1Path);
        assertTorrentFileEqualsPath(decodedTorrent.getFiles().get(1), tmpFs.getPath(testfile2Name));
    }

    /**
     * Test making a simple torrent with multiple files in different folders and non normalized directory
     *
     * @throws IOException on failure
     */
    @Test
    public void testAddingDirectory() throws IOException {
        String folder = "testfolder";
        String file1 = folder + "/testtesttest/./" + testfileName;
        String file2 = "./" + folder + "/" + testfile2Name;
        createDummyFile(123, rootDir.resolve(file1));
        createDummyFile(345, rootDir.resolve(file2));
        TorrentBuilder torrentBuilder = makeTorrentBuilder(null, Arrays.asList(folder));

        Torrent decodedTorrent = buildAndDecodeTorrent(torrentBuilder);

        // sort because the order is not guaranteed when adding by directory
        List<TorrentFile> files = decodedTorrent.getFiles().stream()
                .sorted(Comparator.comparingLong(TorrentFile::getSize))
                .collect(Collectors.toList());

        TorrentFile torrentFile1 = files.get(0);
        Path file1Path = tmpFs.getPath(file1);
        Assert.assertEquals(4, file1Path.getNameCount());
        file1Path = file1Path.normalize();
        Assert.assertEquals(3, file1Path.getNameCount());
        Assert.assertEquals(3, torrentFile1.getPathElements().size());
        assertTorrentFileEqualsPath(torrentFile1, file1Path);
        assertTorrentFileEqualsPath(files.get(1), tmpFs.getPath(file2));
    }

    private void assertTorrentFileEqualsPath(TorrentFile tf, Path path) {
        Path normalized = path.normalize();
        Assert.assertEquals(normalized.getNameCount(), tf.getPathElements().size());
        for (int i = 0; i < normalized.getNameCount(); i++) {
            Assert.assertEquals(normalized.getName(i).toString(), tf.getPathElements().get(i));
        }
    }

    /**
     * Test that multiple announcers is correctly handled
     *
     * @throws IOException on failure
     */
    @Test
    public void testMultipleAnnouncers() throws IOException {
        TorrentBuilder torrentBuilder = makeTorrentBuilder(CREATED_BY, Collections.singletonList(testfileName));
        List<List<String>> announceGroups = Arrays.asList(
                Arrays.asList("http://server1.com", "http://server2.com"),
                Arrays.asList("http://server3.com"),
                Arrays.asList("http://server4.com", "http://server5.com", "http://server6.com")
        );
        for (List<String> announceGroup : announceGroups) {
            torrentBuilder.addAnnounceGroup(announceGroup);
        }
        torrentBuilder.setAnnounce(null);

        Torrent decodedTorrent = buildAndDecodeTorrent(torrentBuilder);
        AnnounceKey key = decodedTorrent.getAnnounceKey().get();
        Assert.assertEquals(announceGroups, key.getTrackerUrls());
        // currently the torrent decoder ignores this if the announce-list is set
        Assert.assertNull(key.getTrackerUrl());

        String explicitAnnounce = "http://explicitannounce.com";
        torrentBuilder.setAnnounce(explicitAnnounce);
        decodedTorrent = buildAndDecodeTorrent(torrentBuilder);
        key = decodedTorrent.getAnnounceKey().get();
        // ensure that announce list is still set
        Assert.assertEquals(announceGroups, key.getTrackerUrls());
        // currently the torrent decoder ignores this if the announce-list is set, even if set explicitly.
        Assert.assertNull(key.getTrackerUrl());
    }

    private Torrent buildAndDecodeTorrent(TorrentBuilder torrentBuilder) {
        byte[] torrent = torrentBuilder.build();
        return metadataService.fromByteArray(torrent);
    }

    /**
     * Test making a simple torrent with a single file
     *
     * @throws IOException on failure
     */
    @Test
    public void testPrivateTorrent() throws IOException {
        TorrentBuilder torrentBuilder = makeTorrentBuilder(CREATED_BY, Collections.singletonList(testfileName));
        torrentBuilder.privateFlag(true);

        Torrent decodedTorrent = buildAndDecodeTorrent(torrentBuilder);
        Assert.assertTrue(decodedTorrent.isPrivate());
    }

    /**
     * Test making a simple torrent with a single file
     *
     * @throws IOException on failure
     */
    @Test
    public void testCreationTime() throws IOException {
        TorrentBuilder torrentBuilder = makeTorrentBuilder(CREATED_BY, Collections.singletonList(testfileName));
        Date creationDate = new Date(123_000L);
        torrentBuilder.creationDate(creationDate);

        Torrent decodedTorrent = buildAndDecodeTorrent(torrentBuilder);
        Assert.assertEquals(creationDate.toInstant(), decodedTorrent.getCreationDate().get());
    }

    /**
     * Test making a simple torrent with a single file
     *
     * @throws IOException on failure
     */
    @Test
    public void testTorrentLengthExactlyOnePieceSize() throws IOException {
        testBoundaryTorrent(1, 0);
    }

    /**
     * Test a torrent on a boundary of a piece, or close to it
     *
     * @param numPieces the number of pieces to make
     * @param offset    the number of bytes off of the exact piece offset. Can be positive or negative, but might not
     *                  test exactly if isn't divisible by two
     * @throws IOException on failure
     */
    private void testBoundaryTorrent(int numPieces, int offset) throws IOException {
        int pieceSize = 1 << 10;
        final int expectedSize = numPieces * pieceSize + offset;
        int fileLength = expectedSize;
        TorrentBuilder torrentBuilder =
                makeTorrentBuilder(CREATED_BY, Collections.singletonList(testfileName), fileLength);
        torrentBuilder.pieceSize(pieceSize);

        Torrent decodedTorrent = buildAndDecodeTorrent(torrentBuilder);
        int expectedNumPieces = offset > 0 ? numPieces + 1 : numPieces;
        assertExpectedFilesAndSizes(expectedNumPieces, expectedSize, decodedTorrent, 1);

        fileLength = numPieces * (pieceSize / 2);
        torrentBuilder = makeTorrentBuilder(CREATED_BY, Arrays.asList(testfileName, testfile2Name), fileLength + offset,
                fileLength);
        torrentBuilder.pieceSize(pieceSize);
        decodedTorrent = buildAndDecodeTorrent(torrentBuilder);
        assertExpectedFilesAndSizes(expectedNumPieces, expectedSize, decodedTorrent, 2);
    }

    private void assertExpectedFilesAndSizes(int expectedNumPieces, int fileLength, Torrent decodedTorrent,
                                             int numExpectedFiles) {
        Assert.assertEquals(expectedNumPieces, Iterators.size(decodedTorrent.getChunkHashes().iterator()));
        Assert.assertEquals(numExpectedFiles, decodedTorrent.getFiles().size());
        Assert.assertEquals(fileLength,
                decodedTorrent.getFiles().stream().mapToLong(TorrentFile::getSize).sum());
    }

    /**
     * Test making a torrent with two pieces exactly on the piece boundary
     *
     * @throws IOException on failure
     */
    @Test
    public void testTorrentLengthExactlyTwoPieceSize() throws IOException {
        testBoundaryTorrent(2, 0);
    }

    /**
     * Test making a torrent with three pieces exactly on the piece boundary
     *
     * @throws IOException on failure
     */
    @Test
    public void testTorrentLengthExactlyThreePieceSize() throws IOException {
        testBoundaryTorrent(3, 0);
    }

    /**
     * Test a torrent which is multiple pieces not on a piece boundary
     *
     * @throws IOException on failure
     */
    @Test
    public void testAlmostAtPieceOffsets() throws IOException {
        testBoundaryTorrent(1, -1);
        testBoundaryTorrent(1, -2);
        testBoundaryTorrent(2, -2);
        testBoundaryTorrent(3, -2);
        testBoundaryTorrent(3, 1);
        testBoundaryTorrent(1, 1);
        testBoundaryTorrent(2, 1);
        testBoundaryTorrent(3, 1);
    }
}
