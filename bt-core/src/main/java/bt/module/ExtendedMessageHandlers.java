package bt.module;

import com.google.inject.BindingAnnotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates individual message handlers,
 * that work with extended protocol message types.
 * Each message type is assigned a unique name,
 * thus annotated value should be a
 * java.util.Map<String,bt.protocol.handler.MessageHandler<? extends bt.protocol.extended.ExtendedMessage>>.
 *
 * @since 1.0
 */
@Target({ ElementType.PARAMETER, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
@BindingAnnotation
public @interface ExtendedMessageHandlers {}
