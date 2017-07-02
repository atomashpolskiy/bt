package bt.processor;

public interface ProcessingStage<C extends ProcessingContext> {

    ProcessingStage<C> execute(C context);
}
