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

import java.util.Objects;
import java.util.Optional;

/**
 * @since 1.6
 */
public class ConnectionResult {

    /**
     * @since 1.6
     */
    public static ConnectionResult success(PeerConnection connection) {
        Objects.requireNonNull(connection);
        return new ConnectionResult(connection, null, null);
    }

    /**
     * @since 1.6
     */
    public static ConnectionResult failure(String message, Throwable error) {
        Objects.requireNonNull(message);
        Objects.requireNonNull(error);
        return new ConnectionResult(null, error, message);
    }

    /**
     * @since 1.6
     */
    public static ConnectionResult failure(String message) {
        Objects.requireNonNull(message);
        return new ConnectionResult(null, null, message);
    }

    private final PeerConnection connection;
    private final Throwable error;
    private final String message;

    private ConnectionResult(PeerConnection connection,
                             Throwable error,
                             String message) {
        this.connection = connection;
        this.error = error;
        this.message = message;
    }

    /**
     * @return true, if the connection attempt has been successful
     * @since 1.6
     */
    public boolean isSuccess() {
        return connection != null;
    }

    /**
     * @return Connection, if {@link #isSuccess()} is true
     * @throws IllegalStateException if {@link #isSuccess()} is false
     */
    public PeerConnection getConnection() {
        if (!isSuccess()) {
            throw new IllegalStateException("Attempt to retrieve connection from unsuccessful result");
        }
        return connection;
    }

    /**
     * @return Optional failure cause, if {@link #isSuccess()} is false
     * @throws IllegalStateException if {@link #isSuccess()} is true
     */
    public Optional<Throwable> getError() {
        if (isSuccess()) {
            throw new IllegalStateException("Attempt to retrieve error from successful result");
        }
        return Optional.ofNullable(error);
    }

    /**
     * @return Optional message
     */
    public Optional<String> getMessage() {
        return Optional.ofNullable(message);
    }
}
