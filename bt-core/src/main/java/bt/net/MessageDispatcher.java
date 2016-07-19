package bt.net;

import bt.protocol.Message;
import bt.service.IShutdownService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class MessageDispatcher implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageDispatcher.class);

    private final Map<Peer, Collection<Consumer<Message>>> consumers;
    private final Map<Peer, Collection<Supplier<Message>>> suppliers;

    private Queue<Peer> disconnectedPeers;

    private volatile boolean shutdown;

    public MessageDispatcher(IShutdownService shutdownService, IPeerConnectionPool pool) {

        consumers = new ConcurrentHashMap<>();
        suppliers = new ConcurrentHashMap<>();

        disconnectedPeers = new LinkedBlockingQueue<>();

        ReentrantLock lock = new ReentrantLock();
        Condition timer = lock.newCondition();

        ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "Message Dispatcher"));
        executor.execute(() -> {
            while (!shutdown) {

                Peer disconnectedPeer;
                while ((disconnectedPeer = disconnectedPeers.poll()) != null) {
                    removePeer(disconnectedPeer);
                }

                if (!consumers.isEmpty()) {
                    Iterator<Map.Entry<Peer, Collection<Consumer<Message>>>> iter = consumers.entrySet().iterator();
                    while (iter.hasNext()) {

                        Map.Entry<Peer, Collection<Consumer<Message>>> entry = iter.next();
                        Peer peer = entry.getKey();
                        Collection<Consumer<Message>> consumers = entry.getValue();

                        IPeerConnection connection = pool.getConnection(peer);
                        if (connection != null) {

                            Message message = null;
                            try {
                                message = connection.readMessageNow();
                            } catch (Exception e) {
                                LOGGER.error("Error when reading message", e);
                                iter.remove();
                                suppliers.remove(peer);
                            }

                            if (message != null) {
                                Iterator<Consumer<Message>> messageConsumers = consumers.iterator();
                                while (messageConsumers.hasNext()) {
                                    Consumer<Message> messageConsumer = messageConsumers.next();
                                    try {
                                        messageConsumer.accept(message);
                                    } catch (Exception e) {
                                        LOGGER.warn("Error in message consumer", e);
                                        messageConsumers.remove();
                                    }
                                }
                            }
                        }
                    }
                }

                if (!suppliers.isEmpty()) {
                    Iterator<Map.Entry<Peer, Collection<Supplier<Message>>>> iter = suppliers.entrySet().iterator();
                    while (iter.hasNext()) {

                        Map.Entry<Peer, Collection<Supplier<Message>>> entry = iter.next();
                        Peer peer = entry.getKey();
                        Collection<Supplier<Message>> suppliers = entry.getValue();

                        IPeerConnection connection = pool.getConnection(peer);
                        if (connection != null) {

                            Iterator<Supplier<Message>> messageSuppliers = suppliers.iterator();
                            while (messageSuppliers.hasNext()) {
                                Supplier<Message> messageSupplier = messageSuppliers.next();
                                Message message = null;
                                try {
                                    message = messageSupplier.get();
                                } catch (Exception e) {
                                    LOGGER.warn("Error in message supplier", e);
                                    messageSuppliers.remove();
                                }

                                if (message != null) {
                                    try {
                                        connection.postMessage(message);
                                    } catch (Exception e) {
                                        LOGGER.error("Error when writing message", e);
                                        iter.remove();
                                        consumers.remove(peer);
                                    }
                                }
                            }
                        }
                    }
                }

                lock.lock();
                try {
                    timer.await(1, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    lock.unlock();
                }
            }
        });

        shutdownService.addShutdownHook(this);
        shutdownService.addShutdownHook(executor::shutdown);
    }

    void removePeer(Peer peer) {
        consumers.remove(peer);
        suppliers.remove(peer);
    }

    void addMessageConsumers(Peer sender, Collection<Consumer<Message>> messageConsumers) {
        consumers.put(sender, messageConsumers);
    }

    void addMessageSuppliers(Peer recipient, Collection<Supplier<Message>> messageSuppliers) {
        suppliers.put(recipient, messageSuppliers);
    }

    @Override
    public void close() throws IOException {
        shutdown = true;
    }
}
