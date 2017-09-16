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

package bt.torrent.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates messaging agents, that act as message producers.
 *
 * <p>Both the annotated method and the containing class must be public and have one of the following lists of parameters:</p>
 * <ul>
 * <li><code>({@link java.util.function.Consumer}&lt;{@link bt.protocol.Message}&gt; consumer,
 * {@link bt.torrent.messaging.MessageContext} context)</code></li>
 * <li><code>({@link java.util.function.Consumer}&lt;{@link bt.protocol.Message}&gt; consumer)</code></li>
 * </ul>
 *
 * @since 1.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Produces {}
