package bt.torrent.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates messaging agents, that act as message producers.
 *
 * <p>Annotated method must be public and have one of the following lists of parameters:</p>
 * <ul>
 * <li>({@link java.util.function.Consumer}&lt;{@link bt.protocol.Message}&gt; consumer,
 * {@link bt.torrent.messaging.MessageContext} context)</li>
 * <li>({@link java.util.function.Consumer}&lt;{@link bt.protocol.Message}&gt; consumer)</li>
 * </ul>
 *
 * @since 1.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Produces {}
