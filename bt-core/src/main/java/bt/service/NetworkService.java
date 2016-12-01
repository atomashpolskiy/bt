package bt.service;

import bt.BtException;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

/**
 *<p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
public class NetworkService implements INetworkService {

    @Override
    public InetAddress getInetAddress() {
        return getInetAddressFromNetworkInterfaces();
    }

    private static InetAddress getInetAddressFromNetworkInterfaces() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress inetAddress = inetAddresses.nextElement();
                    if (!inetAddress.isMulticastAddress() && !inetAddress.isLoopbackAddress() && inetAddress.getAddress().length == 4) {
                        return inetAddress;
                    }
                }
            }

        } catch (SocketException e) {
            throw new BtException("Failed to retrieve network address", e);
        }
        return null;
    }

    @Override
    public int getPort() {
        return 6881;
    }
}
