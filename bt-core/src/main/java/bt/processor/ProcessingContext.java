package bt.processor;

import bt.metainfo.Torrent;
import bt.metainfo.TorrentId;
import bt.torrent.TorrentSessionState;

import java.util.Optional;

/**
 * Aggregates all data, that is specific to and required for some processing chain,
 * and is used to transfer the processing state between different stages of this chain.
 *
 * @since 1.3
 */
public interface ProcessingContext {

    /**
     * @return Torrent ID or {@link Optional#empty()}, if it's not known yet
     * @since 1.3
     */
    Optional<TorrentId> getTorrentId();

    /**
     * @return Torrent or {@link Optional#empty()}, if it's not known yet
     * @since 1.3
     */
    Optional<Torrent> getTorrent();

    /**
     * @return Processing state or {@link Optional#empty()}, if it's not initialized yet
     * @since 1.5
     */
    Optional<TorrentSessionState> getState();
}
