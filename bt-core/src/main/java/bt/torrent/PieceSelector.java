package bt.torrent;

import java.util.stream.Stream;

public interface PieceSelector {

    Stream<Integer> getNextPieces();
}
