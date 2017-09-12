package bt.processor;

public interface ContextFinalizer<C extends ProcessingContext> {

    void finalizeContext(C context);
}
