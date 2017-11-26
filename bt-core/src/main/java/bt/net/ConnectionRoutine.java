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

package bt.net;

import java.net.SocketAddress;

/**
 * Encapsulates a procedure for establishing the connection.
 *
 * @since 1.6
 */
public interface ConnectionRoutine {

    /**
     * @since 1.6
     */
    SocketAddress getRemoteAddress();

    /**
     * Try to establish the connection.
     *
     * @since 1.6
     */
    ConnectionResult establish();

    /**
     * Cancel connection establishing and release related resources.
     *
     * @since 1.6
     */
    void cancel();
}
