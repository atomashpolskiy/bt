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

import bt.module.PeerConnectionSelector;
import bt.net.pipeline.ChannelHandler;
import bt.service.IRuntimeLifecycleBinder;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DataReceivingLoop implements Runnable, DataReceiver {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataReceivingLoop.class);

    private static final int NO_OPS = 0;

    private final SharedSelector selector;

    private volatile boolean shutdown;

    @Inject
    public DataReceivingLoop(@PeerConnectionSelector SharedSelector selector,
                             IRuntimeLifecycleBinder lifecycleBinder) {
        this.selector = selector;

        schedule(lifecycleBinder);
    }

    private void schedule(IRuntimeLifecycleBinder lifecycleBinder) {
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "bt.net.data-receiver"));
        lifecycleBinder.onStartup("Initialize message receiver", () -> executor.execute(this::run));
        lifecycleBinder.onShutdown("Shutdown message receiver", () -> {
            try {
                shutdown();
            } finally {
                executor.shutdownNow();
            }
        });
    }

    //    private synchronized void onPeerConnected(Peer peer) {
//        PeerConnection connection = pool.getConnection(peer);
//        if (connection != null) {
//            try {
//                // drain all buffers before registering
//                Message message;
//                while ((message = connection.readMessageNow()) != null) {
//                    messageSink.accept(peer, message);
//                }
//                if (LOGGER.isTraceEnabled()) {
//                    LOGGER.trace("Registering new connection for peer: {}", peer);
//                }
//                registerConnection(connection);
//            } catch (Exception e) {
//                LOGGER.error("Failed to register new connection for peer: " + peer, e);
//            }
//        }
//    }

    @Override
    public void registerChannel(SelectableChannel channel, ChannelHandler handler) {
        // use atomic wakeup-and-register to prevent blocking of registration,
        // if selection is resumed before call to register is performed
        // (there is a race between the message receiving loop and current thread)
        // TODO: move this to the main loop instead?
        selector.wakeupAndRegister(channel, SelectionKey.OP_READ, handler);
    }

    @Override
    public void unregisterChannel(SelectableChannel channel) {
        selector.keyFor(channel).ifPresent(SelectionKey::cancel);
    }

    @Override
    public void activateChannel(SelectableChannel channel) {
        updateInterestOps(channel, SelectionKey.OP_READ);
    }

    @Override
    public void deactivateChannel(SelectableChannel channel) {
        updateInterestOps(channel, NO_OPS);
    }

//    private int getInterestOps(TorrentId torrentId) {
//        Optional<TorrentDescriptor> descriptor = torrentRegistry.getDescriptor(torrentId);
//        boolean active = descriptor.isPresent() && descriptor.get().isActive();
//        return active ? SelectionKey.OP_READ : NO_OPS;
//    }
//
//    private synchronized void onTorrentStarted(TorrentId torrentId) {
//        updateInterestOps(torrentId, SelectionKey.OP_READ);
//    }
//
//    private synchronized void onTorrentStopped(TorrentId torrentId) {
//        updateInterestOps(torrentId, NO_OPS);
//    }
//
//    private synchronized void updateInterestOps(TorrentId torrentId, int interestOps) {
//        Set<SelectableChannel> _channels = channels.get(torrentId);
//        if (_channels != null && _channels.size() > 0) {
//            _channels.forEach(channel -> updateInterestOps(channel, interestOps));
//        }
//    }

    private void updateInterestOps(SelectableChannel channel, int interestOps) {
        selector.keyFor(channel).ifPresent(key -> {
            // synchronizing on the selection key,
            // as we will be using it in a separate, message receiving thread
            synchronized (key) {
                key.interestOps(interestOps);
            }
        });
    }

    @Override
    public void run() {
        while (!shutdown) {
            if (!selector.isOpen()) {
                LOGGER.info("Selector is closed, stopping...");
                break;
            }

            try {
                // wakeup periodically to check if there are unprocessed keys left
                long t1 = System.nanoTime();
                long timeToBlockMillis = 1000;
                while (selector.select(timeToBlockMillis) == 0) {
                    Thread.yield();
                    long t2 = System.nanoTime();
                    // check that the selection timeout period is expired, before dealing with unprocessed keys;
                    // it could be a call to wake up, that made the select() return,
                    // and we don't want to perform extra work on each spin iteration
                    if ((t2 - t1 >= timeToBlockMillis * 1000) && !selector.selectedKeys().isEmpty()) {
                        // try to deal with unprocessed keys, left from the previous iteration
                        break;
                    }
                }

                Iterator<SelectionKey> selectedKeys = selector.selectedKeys().iterator();
                while (selectedKeys.hasNext()) {
                    try {
                        // do not remove the key if it hasn't been processed,
                        // we'll try again in the next loop iteration
                        if (processKey(selectedKeys.next())) {
                            selectedKeys.remove();
                        }
                    } catch (ClosedSelectorException e) {
                        // selector has been closed, there's no point to continue processing
                        throw e;
                    } catch (Exception e) {
                        LOGGER.error("Failed to process key", e);
                        selectedKeys.remove();
                    }
                }
            } catch (ClosedSelectorException e) {
                LOGGER.info("Selector has been closed, will stop receiving messages...");
                return;
            } catch (IOException e) {
                throw new RuntimeException("Unexpected I/O exception when selecting peer connections", e);
            }
        }
    }

    /**
     * @return true, if the key has been processed and can be removed
     */
    private boolean processKey(final SelectionKey key) {
        ChannelHandler handler;
        Peer peer;

        // synchronizing on the selection key,
        // as we will be updating it in a separate, event-listening thread
        synchronized (key) {
            handler = getHandler(key);
            peer = handler.peer();
            if (!key.isValid() || !key.isReadable()) {
                LOGGER.warn("Selected connection for peer {}, but the key is cancelled or read op is not indicated. Skipping..", peer);
                return false;
            }
        }

//        if (connection.isClosed()) {
//            LOGGER.warn("Selected connection for peer {}, but the connection has already been closed. Skipping..", peer);
//            throw new RuntimeException("Connection closed");
//        }

//        TorrentId torrentId = connection.getTorrentId();
//        if (torrentId == null) {
//            LOGGER.warn("Selected connection for peer {}, but the connection does not indicate a torrent ID. Skipping..", peer);
//            return false;
//        } else if (!torrentRegistry.isSupportedAndActive(torrentId)) {
//            LOGGER.warn("Selected connection for peer {}, but the torrent ID is unknown or inactive: {}. Skipping..", peer, torrentId);
//            return false;
//        }

        handler.fireChannelReady();
//        while (true) {
//            Message message = null;
//            boolean error = false;
//            try {
//                message = connection.readMessageNow();
//            } catch (EOFException e) {
//                if (LOGGER.isDebugEnabled()) {
//                    LOGGER.debug("Received EOF from peer: {}. Disconnecting...", peer);
//                }
//                error = true;
//            } catch (IOException e) {
//                if (LOGGER.isDebugEnabled()) {
//                    LOGGER.debug("I/O error when reading message from peer: {}. Reason: {} ({})",
//                            peer, e.getClass().getName(), e.getMessage());
//                }
//                error = true;
//            } catch (Exception e) {
//                LOGGER.error("Unexpected error when reading message from peer: " + peer, e);
//                error = true;
//            }
//            if (error) {
//                connection.closeQuietly();
//                key.cancel();
//                break;
//            } else if (message == null) {
//                break;
//            } else {
//                // TODO: always accept message, even if error happened
//                messageSink.accept(peer, message);
//            }
//        }
        return true;
    }

    private ChannelHandler getHandler(SelectionKey key) {
        Object obj = key.attachment();
        if (obj == null || !(obj instanceof ChannelHandler)) {
            throw new RuntimeException("Unexpected attachment in selection key: " + obj);
        }
        return (ChannelHandler) obj;
    }

    public void shutdown() {
        shutdown = true;
    }
}
