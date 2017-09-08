package bt.processor;

public interface ProcessingFuture {

    void get();

    void cancel();
}
