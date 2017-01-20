package bt.service;

import bt.runtime.Config;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * @since 1.1
 */
public class LifecycleBinding {

    /**
     * @since 1.1
     */
    public static Builder bind(Runnable r) {
        return new Builder(r);
    }

    private Optional<String> description;
    private Runnable r;
    private boolean async;

    private LifecycleBinding(String description, Runnable r, boolean async) {
        this.description = Optional.ofNullable(description);
        this.r = r;
        this.async = async;
    }

    /**
     * @since 1.1
     */
    public Optional<String> getDescription() {
        return description;
    }

    /**
     * @since 1.1
     */
    public Runnable getRunnable() {
        return r;
    }

    /**
     * @since 1.1
     */
    public boolean isAsync() {
        return async;
    }

    /**
     * @since 1.1
     */
    public static class Builder {

        private Runnable r;
        private String description;
        private boolean async;

        private Builder(Runnable r) {
            this.r = Objects.requireNonNull(r);
        }

        /**
         * @param description Human-readable description
         * @since 1.1
         */
        public Builder description(String description) {
            this.description = Objects.requireNonNull(description);
            return this;
        }

        /**
         * Mark this binding for asynchronous execution
         *
         * @see Config#setShutdownHookTimeout(Duration)
         * @since 1.1
         */
        public Builder async() {
            this.async = true;
            return this;
        }

        /**
         * @since 1.1
         */
        public LifecycleBinding build() {
            return new LifecycleBinding(description, r, async);
        }
    }
}
