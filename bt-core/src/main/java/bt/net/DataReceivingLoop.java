/*
 * Copyright (c) 2016—2021 Andrei Tomashpolskiy and individual contributors.
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
import bt.module.PeerRegistrationQueue;
import bt.net.pipeline.ChannelHandlerContext;
import bt.runtime.Config;
import bt.service.IRuntimeLifecycleBinder;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.IOException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DataReceivingLoop implements Runnable, DataReceiver {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataReceivingLoop.class);

    private static final int NO_OPS = 0;

    private final Selector selector;
    private final Queue<PeerRegistrationEvent> registrationQueue;
    private final ConcurrentMap<SelectableChannel, Integer> interestOpsUpdates;

    private volatile boolean shutdown;

    @Inject
    public DataReceivingLoop(@PeerConnectionSelector Selector selector,
                             @PeerRegistrationQueue ConcurrentLinkedQueue<PeerRegistrationEvent> registrationQueue,
                             IRuntimeLifecycleBinder lifecycleBinder,
                             Config config) {
        this.selector = selector;
        this.registrationQueue = registrationQueue;
        this.interestOpsUpdates = new ConcurrentHashMap<>();

        schedule(lifecycleBinder, config);
    }

    private void schedule(IRuntimeLifecycleBinder lifecycleBinder, Config config) {
        String threadName = String.format("%d.bt.net.data-receiver", config.getAcceptorPort());
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, threadName));
        lifecycleBinder.onStartup("Initialize message receiver", () -> executor.execute(this));
        lifecycleBinder.onShutdown("Shutdown message receiver", () -> {
            try {
                shutdown();
            } finally {
                executor.shutdownNow();
            }
        });
    }

    @Override
    public void registerChannel(SelectableChannel channel, ChannelHandlerContext context) {
        PeerRegistrationEvent peerRegistrationEvent = new PeerRegistrationEvent(channel, SelectionKey.OP_READ, context);
        if (!registrationQueue.add(peerRegistrationEvent)) {
            throw new IllegalStateException("Queue didn't accept event. Should not happen.");
        }
        boolean done;

        do {
            // registration is done in the main receiving thread, which may be waiting in a select call.
            // fire a wakeup event in case this is the case.
            selector.wakeup();
            try {
                // wait up with a timeout for the event to register.
                // There's a slight race condition with the wakeup - it's possible that the receiving loop
                // calls select just after the wakeup call above completes. In this unlikely case, we'll call wakeup
                // again after this timeout which will trigger the event to be processed.
                done = peerRegistrationEvent.waitForCompletion(10);
            } catch (IOException ex) {
                throw new RuntimeException("Failed to register channel", ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ex);
            }
        } while (!done);
    }

    @Override
    public void unregisterChannel(SelectableChannel channel) {
        Optional.ofNullable(channel.keyFor(selector)).ifPresent(SelectionKey::cancel);
    }

    @Override
    public void activateChannel(SelectableChannel channel) {
        updateInterestOps(channel, SelectionKey.OP_READ);
    }

    @Override
    public void deactivateChannel(SelectableChannel channel) {
        updateInterestOps(channel, NO_OPS);
    }

    private void updateInterestOps(SelectableChannel channel, int interestOps) {
        interestOpsUpdates.put(channel, interestOps);
    }

    @Override
    public void run() {
        while (!shutdown) {
            if (!selector.isOpen()) {
                LOGGER.info("Selector is closed, stopping...");
                break;
            }

            try {
                do {
                    processInterestOpsUpdates();
                } while (selector.select(1000) == 0);

                while (!shutdown) {
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
                        } catch (EOFException e) {
                            LOGGER.debug("Channel disconnected", e);
                            selectedKeys.remove();
                        } catch (Exception e) {
                            LOGGER.error("Failed to process key", e);
                            selectedKeys.remove();
                        }
                    }
                    if (selector.selectedKeys().isEmpty()) {
                        break;
                    }

                    if (Thread.interrupted())
                        throw new InterruptedException();

                    processInterestOpsUpdates();
                    selector.selectNow();
                }
            } catch (ClosedSelectorException e) {
                LOGGER.info("Selector has been closed, will stop receiving messages...");
                return;
            } catch (IOException e) {
                throw new RuntimeException("Unexpected I/O exception when selecting peer connections", e);
            } catch (InterruptedException e) {
                LOGGER.info("Terminating due to interrupt");
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void processInterestOpsUpdates() {
        Iterator<Map.Entry<SelectableChannel, Integer>> iter = interestOpsUpdates.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<SelectableChannel, Integer> entry = iter.next();
            SelectableChannel channel = entry.getKey();
            int interestOps = entry.getValue();
            try {
                Optional.ofNullable(entry.getKey().keyFor(selector))
                        .ifPresent(key -> key.interestOps(interestOps));
            } catch (Exception e) {
                LOGGER.error("Failed to set interest ops for channel " + channel + " to " + interestOps, e);
            } finally {
                iter.remove();
            }
        }

        PeerRegistrationEvent peerRegistrationEvent;
        while ((peerRegistrationEvent = this.registrationQueue.poll()) != null) {
            peerRegistrationEvent.register(selector);
        }
    }

    /**
     * @return true, if the key has been processed and can be removed
     */
    private boolean processKey(final SelectionKey key) throws IOException {
        ChannelHandlerContext handler = getHandlerContext(key);
        if (!key.isValid() || !key.isReadable()) {
            return true;
        }
        return handler.readFromChannel();
    }

    private ChannelHandlerContext getHandlerContext(SelectionKey key) {
        Object obj = key.attachment();
        if (!(obj instanceof ChannelHandlerContext)) {
            throw new RuntimeException("Unexpected attachment in selection key: " + obj);
        }
        return (ChannelHandlerContext) obj;
    }

    public void shutdown() {
        shutdown = true;
    }
}
