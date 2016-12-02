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
