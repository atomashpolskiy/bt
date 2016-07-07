package bt.it.fixture;

import java.net.InetAddress;

public interface BtTestRuntimeConfiguration {

    InetAddress getAddress();

    int getPort();
}
