package bt.torrent.messaging;

import bt.protocol.Choke;
import bt.protocol.Message;
import bt.protocol.Unchoke;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;

public class Choker {

    private final Duration CHOKING_THRESHOLD = Duration.ofMillis(10000);

    public void handleConnection(ConnectionState connectionState, Consumer<Message> messageConsumer) {

        Optional<Boolean> shouldChokeOptional = connectionState.getShouldChoke();
        boolean choking = connectionState.isChoking();
        boolean peerInterested = connectionState.isPeerInterested();

        if (!shouldChokeOptional.isPresent()) {
            if (peerInterested && choking) {
                if (mightUnchoke(connectionState)) {
                    shouldChokeOptional = Optional.of(Boolean.FALSE); // should unchoke
                }
            } else if (!peerInterested && !choking) {
                shouldChokeOptional = Optional.of(Boolean.TRUE);
            }
        }

        shouldChokeOptional.ifPresent(shouldChoke -> {
            if (shouldChoke != choking) {
                if (shouldChoke) {
                    // choke immediately
                    connectionState.setChoking(true);
                    messageConsumer.accept(Choke.instance());
                    connectionState.setLastChoked(System.currentTimeMillis());
                } else if (mightUnchoke(connectionState)) {
                    connectionState.setChoking(false);
                    messageConsumer.accept(Unchoke.instance());
                }
            }
        });
    }

    private boolean mightUnchoke(ConnectionState connectionState) {
        // unchoke depending on last choked time to avoid fibrillation
        return System.currentTimeMillis() - connectionState.getLastChoked() >= CHOKING_THRESHOLD.toMillis();
    }
}
