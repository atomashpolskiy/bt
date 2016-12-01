package bt.module;

import com.google.inject.BindingAnnotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates individual message handlers,
 * that work with core BitTorrent message types.
 *
 * <p>Each message type is assigned a unique numeric ID,
 * thus annotated value should be a
 * <code>{@link java.util.Map}&lt;{@link Integer}, {@link bt.protocol.handler.MessageHandler}&gt;</code>.
 *
 * @since 1.0
 */
@Target({ ElementType.PARAMETER, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
@BindingAnnotation
public @interface MessageHandlers {}
