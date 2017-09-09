package bt.processor;

import java.util.concurrent.CompletableFuture;

public class DefaultProcessingFuture implements ProcessingFuture {

    private final CompletableFuture<?> delegate;

    public DefaultProcessingFuture(CompletableFuture<?> delegate) {
        this.delegate = delegate;
    }

    @Override
    public void join() {
        delegate.join();
    }

    @Override
    public void cancel() {
        delegate.complete(null);
    }

    @Override
    public boolean isDone() {
        return delegate.isDone();
    }

    public CompletableFuture<?> getDelegate() {
        return delegate;
    }
}
