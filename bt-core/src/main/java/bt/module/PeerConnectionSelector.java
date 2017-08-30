package bt.module;

import com.google.inject.BindingAnnotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates {@link java.nio.channels.Selector},
 * that should be used for selecting peer connections.
 *
 * @since 1.5
 */
@Target({ ElementType.PARAMETER, ElementType.FIELD, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@BindingAnnotation
public @interface PeerConnectionSelector {}
