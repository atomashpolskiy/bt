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

/**
 * Handles incoming connection requests.
 *
 * @since 1.6
 */
@FunctionalInterface
public interface PeerConnectionAcceptor {

    /**
     * Makes an attempt to accept a new connection and returns a routine for establishing the connection.
     * Blocks until a new incoming connection is available.
     *
     * @return Routine for establishing the connection
     * @since 1.6
     */
    ConnectionRoutine accept();
}
