package bt.processor;

public interface ProcessorFactory {

    <C extends ProcessingContext> Processor<C> processor(Class<C> contextType);
}
