package bt.processor;

public interface ProcessingFuture {

    void join();

    void cancel();

    boolean isDone();
}
