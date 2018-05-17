package bt.net.portmapping.impl;


import bt.net.portmapping.PortMapProtocol;
import bt.net.portmapping.PortMapper;

/**
 * {@link PortMapper} that does nothing.
 */
public class NoOpPortMapper implements PortMapper {

    @Override
    public void mapPort(int port, String address, PortMapProtocol protocol, String mappingDescription) {

    }
}
