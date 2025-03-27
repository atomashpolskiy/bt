package bt.net;

import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.Selector;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * An event that a peer must be registered into the data receiving selector
 */
public class PeerRegistrationEvent {
    private final SelectableChannel channel;
    private final int ops;
    private final Object attachment;
    private final CompletableFuture<?> done = new CompletableFuture<>();

    /**
     * @param channel    the channel to register
     * @param ops        the options to register it with - example: {@link java.nio.channels.SelectionKey#OP_READ}
     * @param attachment the attachment for the channel
     */
    public PeerRegistrationEvent(SelectableChannel channel, int ops, Object attachment) {
        this.channel = channel;
        this.ops = ops;
        this.attachment = attachment;
    }

    /**
     * Register the channel with this selector
     *
     * @param selector the selector to register the channel with
     */
    public void register(Selector selector) {
        try {
            channel.register(selector, ops, attachment);
        } catch (Exception ex) {
            done.completeExceptionally(ex);
            // intentionally eat exception it is sent to the registration thread.
        } catch (Throwable ex) {
            // don't succeed if there's an Error not in the Exception hierarchy
            done.completeExceptionally(ex);
            // don't eat a throwable. It may be an Error we don't want to recover from.
            throw ex;
        } finally {
            // a noop if we already completed in the exception blocks
            done.complete(null);
        }
    }

    /**
     * Wait for the event to be registered
     *
     * @param msTimeout timeout to wait for
     * @return true if the event was registered, false on timeout
     * @throws InterruptedException   on thread interrupt
     * @throws ClosedChannelException if the register failed because the channel was closed
     */
    public boolean waitForCompletion(long msTimeout) throws InterruptedException, ClosedChannelException {
        try {
            done.get(msTimeout, TimeUnit.MILLISECONDS);
            return true;
        } catch (TimeoutException ex) {
            return false;
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof ClosedChannelException)
                throw ((ClosedChannelException) ex.getCause());
            if (ex.getCause() instanceof RuntimeException)
                throw ((RuntimeException) ex.getCause());

            // shouldn't get here..
            throw new IllegalStateException(ex);
        }
    }
}
