package bt.service;

import java.io.Closeable;

public interface IShutdownService {

    void addShutdownHook(Closeable closeable);
}
