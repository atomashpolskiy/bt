package bt.processor;

public interface ContextFinalizer<C extends ProcessingContext> {

    void finish(C context);

    void stop(C context);
}
