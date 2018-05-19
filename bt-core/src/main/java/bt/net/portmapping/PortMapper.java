/*
 * Copyright (c) 2016—2017 Andrei Tomashpolskiy and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package bt.net.portmapping;

/**
 * Service, that maps ports for incoming connections on network gateway.
 *
 * @since 1.8
 */
public interface PortMapper {

    /**
     * Map port for incoming connections on network gateway.
     *
     * @param port mapped port;
     * @param address address to which incoming connections will be forwarded (usually address of current computer);
     * @param protocol network protocol, which will be used on mapped port (TCP/UDP);
     * @param mappingDescription description of the mapping, which will be displayed on network gateway device;
     *
     * @since 1.8
     */
    void mapPort(int port, String address, PortMapProtocol protocol, String mappingDescription);
}
