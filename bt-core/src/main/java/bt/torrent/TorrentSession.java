package bt.torrent;

import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;

import java.util.Optional;

/**
 * Torrent processing session.
 *
 * @since 1.0
 */
public interface TorrentSession {

    /**
     * Returns torrent, that this session is processing.
     * Note that in some cases (e.g. when using magnet links) the torrent may be absent in the beginning.
     *
     * @return Torrent, that this session is processing, or {@link Optional#empty()}, if the torrent hasn't been fetched yet
     * @see #getTorrentId()
     * @since 1.5
     */
    Optional<Torrent> getTorrent();

    /**
     * Returns torrent ID, that this session is processing.
     * Note that in some cases (e.g. when using magnet links) the torrent itself may be absent in the beginning.
     * Use {@link bt.runtime.BtRuntime#service(Class)} to get a {@link TorrentRegistry} and then use
     * {@link TorrentRegistry#getTorrent(TorrentId)} that returns an Optional
     *
     * @return Torrent ID, that this session is processing
     * @see TorrentRegistry
     * @see bt.runtime.BtRuntime#service(Class)
     * @since 1.3
     */
    TorrentId getTorrentId();

    /**
     * @return Current state of torrent processing session
     * @since 1.0
     */
    TorrentSessionState getState();
}
