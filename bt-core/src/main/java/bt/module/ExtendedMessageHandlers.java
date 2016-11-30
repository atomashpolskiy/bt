package bt.module;

import com.google.inject.BindingAnnotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates individual message handlers,
 * that work with extended protocol message types.
 *
 * <p>Each message type is assigned a unique name,
 * thus annotated value should be a
 * {@link java.util.Map}&lt;{@link String}, {@link bt.protocol.handler.MessageHandler}&lt;{@code ? extends} {@link bt.protocol.extended.ExtendedMessage}&gt;&gt;.
 *
 * @since 1.0
 */
@Target({ ElementType.PARAMETER, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
@BindingAnnotation
public @interface ExtendedMessageHandlers {}
