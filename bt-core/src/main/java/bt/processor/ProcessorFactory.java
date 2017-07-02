package bt.processor;

public interface ProcessorFactory {

    <C extends ProcessingContext> ProcessingStage<C> processor(Class<C> contextType);
}
