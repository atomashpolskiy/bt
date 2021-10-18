package bt.net;

public class InetPortUtil {
    /**
     * Check that the port passed in is valid
     *
     * @param port the port to check if is valid
     * @return the port if it is valid
     * @throws IllegalArgumentException if the port is invalid
     */
    public static int checkValidRemotePort(int port) {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Invalid port: " + port);
        }
        return port;
    }

    /**
     * Check that the port passed in is valid
     *
     * @param port the port to check if is valid
     * @return whether the port if it is valid
     */
    public static boolean isValidRemotePort(int port) {
        return port > 0 && port <= 65535;
    }
}
