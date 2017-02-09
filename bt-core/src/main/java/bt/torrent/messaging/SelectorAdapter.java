package bt.torrent.messaging;

import bt.torrent.PieceSelectionStrategy;
import bt.torrent.PieceSelector;
import bt.torrent.PieceStatistics;

import java.util.Arrays;
import java.util.function.Predicate;
import java.util.stream.Stream;

class SelectorAdapter implements PieceSelector {

    private PieceSelectionStrategy selector;
    private PieceStatistics pieceStatistics;
    private Predicate<Integer> validator;

    SelectorAdapter(PieceSelectionStrategy selector, PieceStatistics pieceStatistics, Predicate<Integer> validator) {
        this.selector = selector;
        this.pieceStatistics = pieceStatistics;
        this.validator = validator;
    }

    @Override
    public Stream<Integer> getNextPieces() {
        return Arrays.asList(selector.getNextPieces(pieceStatistics, pieceStatistics.getPiecesTotal(), validator)).stream();
    }
}
