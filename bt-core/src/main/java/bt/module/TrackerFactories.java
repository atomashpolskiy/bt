package bt.module;

import com.google.inject.BindingAnnotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates a collection of tracker factories for different protocols.
 *
 * Annotated value should be a java.util.Map<String,bt.tracker.TrackerFactory>
 *
 * @since 1.0
 */
@Target({ ElementType.PARAMETER, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
@BindingAnnotation
public @interface TrackerFactories {
}
