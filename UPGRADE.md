# UPGRADE INSTRUCTIONS

## 1.7

* `bt.net.IPeerConnectionPool.getConnection` now requires two parameters to uniquely identify the connection: `Peer` and `TorrentId`
* `bt.net.IMessageDispatcher.addMessageConsumer` and `bt.net.IMessageDispatcher.addMessageSupplier` now require an additional parameter: `TorrentId`
* `bt.data.Bitfield.getBitmask` has been renamed to `bt.data.Bitfield.toByteArray`. To get the same value, as was returned by the previous version of this method, use the following invocation:

```java
byte[] bitmask = bitfield.toByteArray(bt.protocol.BitOrder.LITTLE_ENDIAN);
```

* `bt.protocol.Protocols.setBit` and `bt.protocol.Protocols.getBit` now require an additional parameter indicating the order of bits in a byte. To get the same values, as was returned by the previous versions of these methods, use the following invocations:

```java
Protocols.setBit(bytes, bt.protocol.BitOrder.LITTLE_ENDIAN, i);
int bit = Protocols.getBit(bytes, bt.protocol.BitOrder.LITTLE_ENDIAN, i);
```

* Semantics of `bt.data.Bitfield.getPiecesRemaining` have been changed. Previously it returned the number of incomplete and unverified pieces (i.e. `getPiecesTotal() - getPiecesComplete()`). Now it returns the number of incomplete and unverified pieces that should NOT be skipped (i.e. the corresponding files are expected to be downloaded). To get the old behavior, you may use `getPiecesIncomplete()`.
* Semantics of `bt.torrent.TorrentSessionState.getPiecesRemaining` have been changed according to the `bt.data.Bitfield.getPiecesRemaining` changes described above. To get the old behavior, you may use `getPiecesIncomplete()`.

## 1.5

* `bt.BaseClientBuilder#runtime(BtRuntime)` is now protected instead of public. Use a factory method `bt.Bt#client(BtRuntime)` to attach the newly created client to a shared runtime.
* Static contribution methods in `bt.module.ServiceModule` and `bt.module.ProtocolModule` have been replaced by _module extenders_, which provide a clearer and more concise API for contributing custom extensions to the core. Instead of invoking individual contributions methods, downstream modules should now call `bt.module.ServiceModule#extend(Binder)` or `bt.module.ProtocolModule#extend(Binder)` and use methods in the returned builder instance, e.g.:

```java
import com.google.inject.Binder;
import com.google.inject.Module;

import bt.module.ProtocolModule;

public class MyModule implements Module {
    
    @Override
    public void configure(Binder binder) {
        ProtocolModule.extend(binder)
            .addMessageHandler(20, ExtendedProtocol.class)
            .addExtendedMessageHandler("ut_metadata", UtMetadataMessageHandler.class);
    }
}
```
* New centralized mechanism was introduced for publishing/receiving events. It's represented by two DI services: `bt.event.EventSink` for publishing and `bt.event.EventSource` for subscriptions. Core services, that previously provided a custom API for subscribing to events, were updated to use the new mechanism, and the old methods and interfaces have been removed, namely:
    - `bt.net.PeerActivityListener` removed
    - methods were removed in `bt.peer.IPeerRegistry`:
        - `addPeerConsumer(Torrent torrent, Consumer<Peer> consumer)`
        - `addPeerConsumer(TorrentId torrentId, Consumer<Peer> consumer)`
        - `removePeerConsumers(Torrent torrent)`
        - `removePeerConsumers(TorrentId torrentId)`
    - method was removed in `bt.net.IPeerConnectionPool`:
        - `addConnectionListener(PeerActivityListener listener)`
* `bt.torrent.TorrentSession` and `bt.runtime.BtClient#getSession()` have been removed. Here's what to use to get the information, that could previously be retrieved from the session:
  - Torrent ID and Torrent object: use `bt.TorrentClientBuilder#afterTorrentFetched(Consumer<Torrent>)` to be notified, when the torrent is fetched
  - Session state: use asynchronous listener via `bt.runtime.BtClient#startAsync(java.util.function.Consumer<bt.torrent.TorrentSessionState>, long)`
