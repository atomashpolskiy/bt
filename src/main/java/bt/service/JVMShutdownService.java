package bt.service;

import java.time.Duration;

public class JVMShutdownService extends BaseShutdownService {

    public JVMShutdownService() {

        super(Duration.ofSeconds(1));

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                shutdown();
            }
        });
    }

    @Override
    protected void onError(Throwable e) {
        e.printStackTrace(System.err);
        System.err.flush();
    }

    @Override
    public void shutdownNow() {
        shutdown();
    }
}
