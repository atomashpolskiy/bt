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
import bt.net.pipeline.ChannelHandlerContext;
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

    @Override
    public void registerChannel(SelectableChannel channel, ChannelHandlerContext context) {
        // use atomic wakeup-and-register to prevent blocking of registration,
        // if selection is resumed before call to register is performed
        // (there is a race between the message receiving loop and current thread)
        // TODO: move this to the main loop instead?
        selector.wakeupAndRegister(channel, SelectionKey.OP_READ, context);
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
        ChannelHandlerContext handler;

        // synchronizing on the selection key,
        // as we will be updating it in a separate, event-listening thread
        synchronized (key) {
            handler = getHandlerContext(key);
            if (!key.isValid() || !key.isReadable()) {
                return false;
            }
        }

        handler.fireChannelReady();
        return true;
    }

    private ChannelHandlerContext getHandlerContext(SelectionKey key) {
        Object obj = key.attachment();
        if (obj == null || !(obj instanceof ChannelHandlerContext)) {
            throw new RuntimeException("Unexpected attachment in selection key: " + obj);
        }
        return (ChannelHandlerContext) obj;
    }

    public void shutdown() {
        shutdown = true;
    }
}
