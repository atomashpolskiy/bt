package bt.torrent.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates messaging agents, that act as message producers.
 *
 * Annotated method must be public and have one of the following lists of parameters:
 * - (Consumer<Message> consumer, {@link bt.torrent.messaging.MessageContext} context),
 * - (Consumer<Message> consumer)
 *
 * @since 1.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Produces {}
