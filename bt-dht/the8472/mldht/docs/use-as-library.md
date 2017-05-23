# Using mldht as a library


## Initialization of a DHT Node

1. create one `lbms.plugins.mldht.kad.DHT` instance per IP address family
2. `addStatusListener` to get notified when initialization is complete
3. (optional) provide custom Executors, logging, tie IPv4 and IPv6 instances together as siblings. See `the8472.mldht.Launcher` as an advanced example for initialization.  
4. `start()` them
5. use `dht.getServerManager().awaitActiveServer()` to wait for a socket to become ready for use

## Performing basic tasks

While mldht implements low level concepts such as individual DHT messages and query-response exchanges on top of them, most common tasks will require dozens of such message exchanges.
The necessary logic is encapsulated in `Task` subclasses. The most common actions are the `PeerLookupTask` and `AnnounceTask` which fetch peer lists for torrents and announce your torrent listening port.

You can find examples how most Tasks, including BEP44 get and put, are used in the `the8472.mldht.cli.commands` package.

The utility methods `DHT.createPeerLookup(byte[])` and `DHT.announce(PeerLookupTask, boolean, int)` can be used to avoid some boilerplate for simple uses.

## Bootstrapping

Depending on the configuration passed during startup the DHT will try to read previously stored contacts from disk or contact well-known bootstrap nodes. If neither mechanism works
you can manually bootstrap by adding nodes via `lbms.plugins.mldht.kad.DHT.addDHTNode(String, int)`.

## Debugging

The `the8472.mldht.Diagnostics` class can be used to write out most of the DHT state to a log directory in a human-readable format.
`DHT.setLogger(DHTLogger)` and `DHT.setLogLevel(LogLevel)` can log most events, down to individual messages.


## Multihoming, IPv4, IPv6

Tasks are always bound to a single socket. If you want to announce  multiple interfaces or multiple address families you will have to run the tasks in question on each available `RPCServer` instance.


## Hooking into stream of incoming messages

After creating `DHT` instances, register a callback via `addIncomingMessageListener(DHT.IncomingMessageListener l)`. It will be called for most incoming messages. Some but not all bogus/invalid ones will be prefiltered.

The callback is called from the message processing threads, so it should be non-blocking and thread-safe.

Message objects and their contents should not be modified.  