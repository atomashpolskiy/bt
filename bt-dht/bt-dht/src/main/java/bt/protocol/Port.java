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

package bt.protocol;

import bt.protocol.handler.PortMessageHandler;

/**
 * @since 1.1
 */
public final class Port implements Message {

    private int port;

    /**
     * @param port Port, on which the DHT service is listening on
     * @throws InvalidMessageException If port number is out of the allowed range (0-65535)
     * @since 1.1
     */
    public Port(int port) throws InvalidMessageException {

        if (port < 0 || port > 65535) {
            throw new InvalidMessageException("Invalid argument: port (" + port + ")");
        }

        this.port = port;
    }

    /**
     * @since 1.1
     */
    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "] port {" + port + "}";
    }

    @Override
    public Integer getMessageId() {
        return PortMessageHandler.PORT_ID;
    }
}
