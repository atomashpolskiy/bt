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

import bt.protocol.Message;

import java.lang.invoke.MethodHandle;

/**
 * Instances of this class provide callbacks for the {@link MessagingAgentCompiler}.
 *
 * @since 1.0
 */
public interface CompilerVisitor {

    /**
     * Visit a message consumer method.
     *
     * @param consumedType Class, representing a message type,
     *                     that this consumer is interested in
     * @param handle Method handle. Method arity is 1 or 2.
     * @see bt.torrent.annotation.Consumes
     * @param <T> (A subtype of) Message type
     * @since 1.0
     */
    <T extends Message> void visitConsumer(Class<T> consumedType, MethodHandle handle);

    /**
     * Visit a message producer method.
     *
     * @param handle Method handle. Method arity is 1 or 2.
     * @see bt.torrent.annotation.Consumes
     * @since 1.0
     */
    void visitProducer(MethodHandle handle);
}
