package bt.torrent.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates messaging agents, that act as message consumers.
 *
 * <p>Annotated method must be public and have one of the following lists of parameters:</p>
 * <ul>
 * <li><code>(T message, {@link bt.torrent.messaging.MessageContext} context)</code></li>
 * <li><code>(T message)</code></li>
 * </ul>
 * <p>where T is a (subtype of) {@link bt.protocol.Message}.</p>
 *
 * <p>For instance, consumer of {@link bt.protocol.Piece} messages may have one of two forms:</p>
 * <ul>
 * <li>{@code public void consume(Piece piece, MessageContext context)}</li>
 * <li>{@code public void consume(Piece piece)}</li>
 * </ul>
 *
 * <p>A generic consumer, that is interested in receiving all types of messages:</p>
 * <ul>
 * <li>{@code public void consume(Message message, MessageContext context)}</li>
 * <li>{@code public void consume(Message message)}</li>
 * </ul>
 *
 * @see bt.torrent.messaging.MessageConsumer
 * @since 1.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Consumes {}
