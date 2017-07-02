package bt.processor;

public abstract class BaseProcessingStage<C extends ProcessingContext> implements ProcessingStage<C> {

    private final ProcessingStage<C> next;

    public BaseProcessingStage(ProcessingStage<C> next) {
        this.next = next;
    }

    @Override
    public ProcessingStage<C> execute(C context) {
        doExecute(context);
        return next;
    }

    protected abstract void doExecute(C context);
}
