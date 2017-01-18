package bt.test.protocol;

import bt.protocol.Handshake;

import java.util.function.BiPredicate;

import static org.junit.Assert.assertEquals;

final class HandshakeMatcher implements BiPredicate<Handshake, Handshake> {

    @Override
    public boolean test(Handshake handshake, Handshake handshake2) {
        assertEquals(handshake.getTorrentId(), handshake2.getTorrentId());
        assertEquals(handshake.getPeerId(), handshake2.getPeerId());
        return true;
    }
}
