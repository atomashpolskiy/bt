package bt.module;

import com.google.inject.Module;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates a method in a {@link Module},
 * that would like to make a contribution
 * to some other module in the same runtime.
 *
 * Annotated method should have a single parameter
 * of the type denoted by annotation's {@link #value()}.
 *
 * @since 1.0
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Contribute {

    /**
     * Type of the module, that should be passed into the annotated method.
     *
     * @since 1.0
     */
    Class<? extends Module> value();
}
