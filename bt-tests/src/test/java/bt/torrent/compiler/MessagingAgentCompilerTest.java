/*
 * Copyright (c) 2016—2017 Andrei Tomashpolskiy and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package bt.torrent.compiler;

import bt.protocol.Bitfield;
import bt.protocol.Have;
import bt.protocol.KeepAlive;
import bt.protocol.Message;
import bt.protocol.Piece;
import bt.torrent.annotation.Consumes;
import bt.torrent.annotation.Produces;
import bt.torrent.messaging.MessageContext;
import org.junit.Before;
import org.junit.Test;

import java.lang.invoke.MethodHandle;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MessagingAgentCompilerTest {

    private MessagingAgentCompiler compiler;

    @Before
    public void setUp() {
        compiler = new MessagingAgentCompiler();
    }

    public class C1 {

        private boolean executed;

        public boolean isExecuted() {
            return executed;
        }

        @Consumes
        public void consume(Message message, MessageContext context) {
            this.executed = true;
        }
    }

    public class C2 {

        @Consumes
        public void consume(Bitfield bitfield, MessageContext context) {}

        @Consumes
        public void consume(Have have) {}

        @Consumes
        public void consume(Piece piece, MessageContext context) {}
    }

    public class C3 {

        private boolean executed;

        public boolean isExecuted() {
            return executed;
        }

        @Consumes
        public void consume(KeepAlive keepAlive) {
            this.executed = true;
        }
    }

    public class C4 {

        @Consumes
        public void consume(Object object, MessageContext context) {}
    }

    public class C5 extends C3 {

    }

    public class P1 {

        private boolean executed;

        public boolean isExecuted() {
            return executed;
        }

        @Produces
        public void produce(Consumer<Message> messageConsumer, MessageContext context) {
            this.executed = true;
        }
    }

    public class P2 {

        private boolean executed;

        public boolean isExecuted() {
            return executed;
        }

        @Produces
        public void produce1(Consumer<Message> messageConsumer, MessageContext context) {}

        @Produces
        public void produce2(Consumer<Message> messageConsumer) {
            this.executed = true;
        }
    }

    public class P3 {

        private boolean executed;

        public boolean isExecuted() {
            return executed;
        }

        @Produces
        public void produce(Consumer<Message> messageConsumer) {
            this.executed = true;
        }
    }

    @Test
    public void testCompiler_Consumer_Generic() {

        Class<?>[] consumedType = new Class<?>[1];
        CompilerVisitor visitor = createVisitor((type, handle) -> consumedType[0] = type, null);

        compiler.compileAndVisit(new C1(), visitor);

        assertNotNull(consumedType[0]);
        assertEquals(Message.class, consumedType[0]);
    }

    @Test
    public void testCompiler_Consumer_MultipleTypes() {

        Set<Class<?>> consumedTypes = new HashSet<>();
        CompilerVisitor visitor = createVisitor((type, handle) -> consumedTypes.add(type), null);

        compiler.compileAndVisit(new C2(), visitor);

        assertEquals(3, consumedTypes.size());
        assertTrue(consumedTypes.containsAll(Arrays.asList(Bitfield.class, Piece.class, Have.class)));
    }

    @Test
    public void testCompiler_Consumer_FullHandle() {

        C1 c1 = new C1();

        CompilerVisitor visitor = createVisitor((type, handle) -> {
            try {
                handle.invoke(c1, null, null);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }, null);

        compiler.compileAndVisit(c1, visitor);

        assertTrue(c1.isExecuted());
    }

    @Test
    public void testCompiler_Consumer_SingleParameterHandle() {

        C3 c3 = new C3();

        CompilerVisitor visitor = createVisitor((type, handle) -> {
            try {
                handle.invoke(c3, null);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }, null);

        compiler.compileAndVisit(c3, visitor);

        assertTrue(c3.isExecuted());
    }

    @Test
    public void testCompiler_Consumer_Inherited() {

        Class<?>[] consumedType = new Class<?>[1];
        CompilerVisitor visitor = createVisitor((type, handle) -> consumedType[0] = type, null);

        compiler.compileAndVisit(new C5(), visitor);

        assertNotNull(consumedType[0]);
        assertEquals(KeepAlive.class, consumedType[0]);
    }

    @Test
    public void testCompiler_Producer() {

        Boolean[] compiled = new Boolean[] {false};
        CompilerVisitor visitor = createVisitor(null, handle -> compiled[0] = true);

        compiler.compileAndVisit(new P1(), visitor);

        assertTrue(compiled[0]);
    }

    @Test
    public void testCompiler_Producer_MultipleProducers() {

        int[] producerCount = new int[1];
        CompilerVisitor visitor = createVisitor(null, handle -> producerCount[0]++);

        compiler.compileAndVisit(new P2(), visitor);

        assertEquals(2, producerCount[0]);
    }

    @Test
    public void testCompiler_Producer_FullHandle() {

        P1 p1 = new P1();

        CompilerVisitor visitor = createVisitor(null, handle -> {
            try {
                handle.invoke(p1, null, null);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });

        compiler.compileAndVisit(p1, visitor);

        assertTrue(p1.isExecuted());
    }

    @Test
    public void testCompiler_Producer_SingleParameterHandle() {

        P3 p3 = new P3();

        CompilerVisitor visitor = createVisitor(null, handle -> {
            try {
                handle.invoke(p3, null);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });

        compiler.compileAndVisit(p3, visitor);

        assertTrue(p3.isExecuted());
    }

    @Test
    public void testCompiler_Consumer_WrongParameters() {

        Exception e = null;
        try {
            compiler.compileAndVisit(new C4(), createVisitor((c, h) -> {}, h -> {}));
        } catch (Exception e1) {
            e = e1;
        }

        assertNotNull(e);
        assertEquals("Consumer method must have bt.protocol.Message or it's subclass as the first parameter", e.getMessage());
    }

    private static CompilerVisitor createVisitor(BiConsumer<Class<?>, MethodHandle> consumerVisitor,
                                                 Consumer<MethodHandle> producerVisitor) {
        return new CompilerVisitor() {
            @Override
            public <T extends Message> void visitConsumer(Class<T> consumedType, MethodHandle handle) {
                if (consumerVisitor == null) {
                    throw new IllegalStateException("Not expecting a message consumer");
                } else {
                    consumerVisitor.accept(consumedType, handle);
                }
            }

            @Override
            public void visitProducer(MethodHandle handle) {
                if (producerVisitor == null) {
                    throw new IllegalStateException("Not expecting a message producer");
                } else {
                    producerVisitor.accept(handle);
                }
            }
        };
    }
}
