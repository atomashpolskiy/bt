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

package bt.tracker.udp;

import bt.protocol.Protocols;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Random;

public class SingleClientUdpTracker extends ExternalResource implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(SingleClientUdpTracker.class);

    private static final Duration CONNECTION_EXPIRATION_TIME = Duration.ofMinutes(2);
    private static final long EPHEMERAL_CONNECTION_ID = 0x41727101980L;

    private static final int CONNECT_CODE = 0;
    private static final int ANNOUNCE_CODE = 1;
    private static final int ERROR_CODE = 3;

    private final DatagramSocket serverSocket;
    private final int interval;
    private final int leechers;
    private final int seeders;

    private volatile boolean shutdown;
    private volatile long currentConnection;
    private volatile long connectedOn;

    public SingleClientUdpTracker(int interval, int leechers, int seeders) {
        try {
            this.serverSocket = new DatagramSocket(new InetSocketAddress(Inet4Address.getLoopbackAddress(), 0));
        } catch (SocketException e) {
            throw new RuntimeException("Failed to create server socket", e);
        }

        this.interval = interval;
        this.leechers = leechers;
        this.seeders = seeders;
    }

    public SocketAddress getServerAddress() {
        return serverSocket.getLocalSocketAddress();
    }

    @Override
    public void run() {
        LOGGER.info("Listening on {}", getServerAddress());
        while (!shutdown) {
            byte[] rbuf = new byte[8192];
            DatagramPacket received = new DatagramPacket(rbuf, rbuf.length);
            try {
                serverSocket.receive(received);
            } catch (IOException e) {
                throw new RuntimeException("Unexpected I/O error", e);
            }

            byte[] response = processRequest(Arrays.copyOfRange(rbuf, 0, received.getLength()));
            DatagramPacket sent = new DatagramPacket(response, response.length, received.getSocketAddress());
            try {
                serverSocket.send(sent);
                LOGGER.info("Sent message {}", getServerAddress());
            } catch (IOException e) {
                throw new RuntimeException("Unexpected I/O error", e);
            }
        }
    }

    private byte[] processRequest(byte[] requestData) {
        long connectionId = Protocols.readLong(requestData, 0);
        int action = Protocols.readInt(requestData, 8);
        int transactionId = Protocols.readInt(requestData, 12);
        boolean connectionExpired = System.currentTimeMillis() - connectedOn > CONNECTION_EXPIRATION_TIME.toMillis();

        switch (action) {
            case CONNECT_CODE: {
                if (connectionId != EPHEMERAL_CONNECTION_ID) {
                    return createError(transactionId,
                            "Connect request should use " + EPHEMERAL_CONNECTION_ID + " as connection ID");
                } else if (!connectionExpired) {
                    return createError(transactionId, "Connection is not expired yet: " + connectionId);
                }
                currentConnection = generateConnectionId();
                connectedOn = System.currentTimeMillis();
                return createConnectResponse(currentConnection, transactionId);
            }
            case ANNOUNCE_CODE: {
                if (currentConnection != connectionId) {
                    return createError(transactionId, "Unknown connection: " + connectionId);
                } else if (connectionExpired) {
                    return createError(transactionId, "Connection expired: " + connectionId);
                }
                return createAnnounceResponse(currentConnection, transactionId);
            }
            default: {
                return createError(transactionId, "Unsupported action: " + action);
            }
        }
    }

    private long generateConnectionId() {
        return new Random(System.currentTimeMillis()).nextLong();
    }

    private byte[] createConnectResponse(long connectionId, int transactionId) {
        byte[] data = new byte[4 + 4 + 8];
        System.arraycopy(Protocols.getIntBytes(CONNECT_CODE), 0, data, 0, 4);
        System.arraycopy(Protocols.getIntBytes(transactionId), 0, data, 4, 4);
        System.arraycopy(Protocols.getLongBytes(connectionId), 0, data, 8, 8);
        return data;
    }

    private byte[] createAnnounceResponse(long connectionId, int transactionId) {
        byte[] data = new byte[4 + 4 + 4 + 4 + 4];
        System.arraycopy(Protocols.getIntBytes(ANNOUNCE_CODE), 0, data, 0, 4);
        System.arraycopy(Protocols.getIntBytes(transactionId), 0, data, 4, 4);
        System.arraycopy(Protocols.getIntBytes(interval), 0, data, 8, 4);
        System.arraycopy(Protocols.getIntBytes(leechers), 0, data, 12, 4);
        System.arraycopy(Protocols.getIntBytes(seeders), 0, data, 16, 4);
        return data;
    }

    private byte[] createError(int transactionId, String message) {
        byte[] messageBytes;
        try {
             messageBytes = message.getBytes("ASCII");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Unexpected error", e);
        }

        byte[] data = new byte[4 + 4 + messageBytes.length];
        System.arraycopy(Protocols.getIntBytes(ERROR_CODE), 0, data, 0, 4);
        System.arraycopy(Protocols.getIntBytes(transactionId), 0, data, 4, 4);
        System.arraycopy(messageBytes, 0, data, 8, messageBytes.length);
        return data;
    }

    @Override
    protected void after() {
        shutdown();
    }

    public void shutdown() {
        this.shutdown = true;
        try {
            this.serverSocket.close();
        } catch (Exception e) {
            // ignore
        }
    }
}
