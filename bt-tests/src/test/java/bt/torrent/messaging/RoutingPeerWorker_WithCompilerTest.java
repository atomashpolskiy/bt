package bt.torrent.messaging;

import bt.net.Peer;
import bt.protocol.Have;
import bt.protocol.Message;
import bt.protocol.Piece;
import bt.torrent.annotation.Consumes;
import bt.torrent.annotation.Produces;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class RoutingPeerWorker_WithCompilerTest {

    private C1 c1;
    private P1 p1;
    private IPeerWorker peerWorker;

    @Before
    public void setUp() {
        this.c1 = new C1();
        this.p1 = new P1();

        Set<Object> agents = new HashSet<Object>() {{
            add(c1);
            add(p1);
        }};

        IPeerWorkerFactory peerWorkerFactory = new PeerWorkerFactory(agents);
        this.peerWorker = peerWorkerFactory.createPeerWorker(mock(Peer.class));
    }

    private interface Executable {
        Set<String> getMethods();
        Set<String> getExecutedMethods();
    }

    public class C1 implements Executable {

        private Set<String> methods;
        private Set<String> executedMethods;

        public C1() {
            this.methods = new HashSet<String>() {{
                add("consume_generic");
                add("consume_piece1");
                add("consume_piece2");
                add("consume_have");
            }};
            this.executedMethods = new HashSet<>();
        }

        @Override
        public Set<String> getMethods() {
            return methods;
        }

        @Override
        public Set<String> getExecutedMethods() {
            return executedMethods;
        }

        @Consumes
        public void consume_generic(Message message) {
            executedMethods.add("consume_generic");
        }

        @Consumes
        public void consume_piece1(Piece piece, MessageContext context) {
            executedMethods.add("consume_piece1");
        }

        @Consumes
        public void consume_piece2(Piece piece, MessageContext context) {
            executedMethods.add("consume_piece2");
        }

        @Consumes
        public void consume_have(Have have, MessageContext context) {
            executedMethods.add("consume_have");
        }
    }

    public class P1 implements Executable {

        private Set<String> methods;
        private Set<String> executedMethods;

        public P1() {
            this.methods = new HashSet<String>() {{
                add("produce1");
                add("produce2");
            }};
            this.executedMethods = new HashSet<>();
        }

        @Override
        public Set<String> getMethods() {
            return methods;
        }

        @Override
        public Set<String> getExecutedMethods() {
            return executedMethods;
        }

        @Produces
        public void produce1(Consumer<Message> messageConsumer, MessageContext context) {
            executedMethods.add("produce1");
        }

        @Produces
        public void produce2(Consumer<Message> messageConsumer) {
            executedMethods.add("produce2");
        }
    }

    @Test
    public void testPeerWorker_Consumer() {
        peerWorker.accept(new Piece(0,0,new byte[1]));
        assertAllExecuted(c1, Arrays.asList("consume_generic", "consume_piece1", "consume_piece2"));
    }

    @Test
    public void testPeerWorker_Producer() {
        peerWorker.get();
        assertAllExecuted(p1, Arrays.asList("produce1", "produce2"));
    }

    private static void assertAllExecuted(Executable executable, Collection<String> executedMethods) {
        executable.getMethods().forEach(method -> {
            if (executedMethods.contains(method)) {
                assertTrue("expected method was not executed: " + method, executable.getExecutedMethods().contains(method));
            } else {
                assertFalse("unexpected method was executed: " + method, executable.getExecutedMethods().contains(method));
            }
        });
    }
}
