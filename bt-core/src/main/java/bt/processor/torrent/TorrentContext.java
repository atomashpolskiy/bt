package bt.processor.torrent;

import bt.data.Bitfield;
import bt.data.Storage;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;
import bt.processor.ProcessingContext;
import bt.torrent.BitfieldBasedStatistics;
import bt.torrent.TorrentSession;
import bt.torrent.messaging.Assignments;
import bt.torrent.messaging.MessageRouter;
import bt.torrent.selector.PieceSelector;
import bt.tracker.TrackerAnnouncer;

import java.util.Optional;
import java.util.function.Supplier;

public class TorrentContext implements ProcessingContext {

    private final PieceSelector pieceSelector;
    private final Storage storage;
    private final Supplier<Torrent> torrentSupplier;

    /* all of these can be missing, depending on which stage is currently being executed */
    private volatile TorrentId torrentId;
    private volatile Torrent torrent;
    private volatile TorrentSession session;
    private volatile MessageRouter router;
    private volatile Bitfield bitfield;
    private volatile Assignments assignments;
    private volatile BitfieldBasedStatistics pieceStatistics;
    private volatile TrackerAnnouncer announcer;

    public TorrentContext(PieceSelector pieceSelector,
                          Storage storage,
                          Supplier<Torrent> torrentSupplier) {
        this.pieceSelector = pieceSelector;
        this.storage = storage;
        this.torrentSupplier = torrentSupplier;
    }

    public PieceSelector getPieceSelector() {
        return pieceSelector;
    }

    public Storage getStorage() {
        return storage;
    }

    public Supplier<Torrent> getTorrentSupplier() {
        return torrentSupplier;
    }

    ///////////////////////////////////////////////

    @Override
    public Optional<TorrentId> getTorrentId() {
        return Optional.ofNullable(torrentId);
    }

    public void setTorrentId(TorrentId torrentId) {
        this.torrentId = torrentId;
    }

    @Override
    public Optional<Torrent> getTorrent() {
        return Optional.ofNullable(torrent);
    }

    public void setTorrent(Torrent torrent) {
        this.torrent = torrent;
    }

    @Override
    public Optional<TorrentSession> getSession() {
        return Optional.ofNullable(session);
    }

    public void setSession(TorrentSession session) {
        this.session = session;
    }

    public MessageRouter getRouter() {
        return router;
    }

    public void setRouter(MessageRouter router) {
        this.router = router;
    }

    public Bitfield getBitfield() {
        return bitfield;
    }

    public void setBitfield(Bitfield bitfield) {
        this.bitfield = bitfield;
    }

    public Assignments getAssignments() {
        return assignments;
    }

    public void setAssignments(Assignments assignments) {
        this.assignments = assignments;
    }

    public BitfieldBasedStatistics getPieceStatistics() {
        return pieceStatistics;
    }

    public void setPieceStatistics(BitfieldBasedStatistics pieceStatistics) {
        this.pieceStatistics = pieceStatistics;
    }

    public Optional<TrackerAnnouncer> getAnnouncer() {
        return Optional.ofNullable(announcer);
    }

    public void setAnnouncer(TrackerAnnouncer announcer) {
        this.announcer = announcer;
    }
}
