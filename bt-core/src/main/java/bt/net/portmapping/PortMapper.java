package bt.net.portmapping;

/**
 * Service, that maps ports for incoming connections on network gateway.
 */
public interface PortMapper {

    /**
     * Map port for incoming connections on network gateway.
     *
     * @param port mapped port;
     * @param address address to which incoming connections will be forwarded (usually address of current computer);
     * @param protocol network protocol, which will be used on mapped port (TCP/UDP);
     * @param mappingDescription description of the mapping, which will be displayed on network gateway device;
     */
    void mapPort(int port, String address, PortMapProtocol protocol, String mappingDescription);
}
