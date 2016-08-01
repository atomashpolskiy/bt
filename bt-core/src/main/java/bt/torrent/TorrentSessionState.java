package bt.torrent;

import bt.net.Peer;

import java.util.Set;

public interface TorrentSessionState {

    int getPiecesTotal();

    int getPiecesRemaining();

    long getDownloaded();

    long getUploaded();

    Set<Peer> getConnectedPeers();
}
