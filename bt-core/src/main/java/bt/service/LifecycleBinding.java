package bt.service;

import java.util.Objects;
import java.util.Optional;

/**
 * @since 1.1
 */
public class LifecycleBinding {

    static Builder bindRunnable(Runnable r) {
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

    static class Builder {

        private Runnable r;
        private String description;
        private boolean async;

        private Builder(Runnable r) {
            this.r = Objects.requireNonNull(r);
        }

        Builder description(String description) {
            this.description = Objects.requireNonNull(description);
            return this;
        }

        Builder async() {
            this.async = true;
            return this;
        }

        LifecycleBinding build() {
            return new LifecycleBinding(description, r, async);
        }
    }
}
