package bt.torrent.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates messaging agents, that act as message consumers.
 *
 * Annotated method must be public and have one of the following lists of parameters:
 * - (T message, {@link bt.torrent.messaging.MessageContext} context),
 * - (T message),
 * where T is (a subtype of) {@link bt.protocol.Message}.
 *
 * For instance, consumer of {@link bt.protocol.Piece} messages may have one of two forms:
 * - public void consume(Piece piece, MessageContext context)
 * - public void consume(Piece piece)
 *
 * A generic consumer, that is interested in receiving all types of messages:
 * - public void consume(Message message, MessageContext context)
 * - public void consume(Message message)
 *
 * @see bt.torrent.messaging.MessageConsumer
 * @since 1.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Consumes {}
