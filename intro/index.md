---
layout: page
title: A Brief Introduction
permalink: /intro/
---

# **Setup**

## _**Maven**_

Declare the following dependencies in your project's **pom.xml**:

```xml
<dependency>
    <groupId>com.github.atomashpolskiy</groupId>
    <artifactId>bt-core</artifactId>
    <version>1.0</version>
</dependency>
<!-- for the sake of keeping the core with minimum number of 3-rd party 
     dependencies HTTP tracker support is shipped as a separate module;
     you may omit this dependency if only UDP trackers are going to be used -->
<dependency>
    <groupId>com.github.atomashpolskiy</groupId>
    <artifactId>bt-http-tracker-client</artifactId>
    <version>1.0</version>
</dependency>
```

# **Command-line launcher**
 
Bt CLI client is a very simple program for downloading/seeding a single torrent. It illustrates the most basic use case of Bt library.

See [usage notes](https://github.com/atomashpolskiy/bt/tree/master/bt-cli) and explore the [CliClient](https://github.com/atomashpolskiy/bt/blob/master/bt-cli/src/main/java/bt/cli/CliClient.java) class for an example of assembling a basic Bt client.

# **Overall design**

## _**Private and shared runtimes**_

Each Bt client is split into two parts:

- shared runtime
- torrent workers

Main idea is that there is a number of shared services and resources, that can be re-used by multiple torrent sessions. These include:

- incoming connection acceptors
- thread pools
- tracker connections
- configuration
- etc.

Hence, there are two ways to create a Bt client:

**1) Client with a private runtime**

```java
Storage storage = new FileSystemStorage(/* target directory */);

BtClient client = Bt.client(storage).url(/* torrent file URL */).standalone();

client.startAsync().join();
```

**2) Client, attached to a shared runtime**

```java
Storage storage = new FileSystemStorage(/* target directory */);

BtRuntime sharedRuntime = BtRuntime.defaultRuntime();

URL url1 = /* torrent file URL #1 */,
    url2 = /* torrent file URL #2 */;

BtClient client1 = Bt.client(storage).url(url1).attachToRuntime(sharedRuntime);
BtClient client2 = Bt.client(storage).url(url2).attachToRuntime(sharedRuntime);

CompletableFuture.allOf(client1.startAsync(), client2.startAsync()).join();
```

As a bonus, in the latter case the main thread will wait until both torrents are completed.

## _**Modular architecture**_

Bt is built around [Google Guice](https://github.com/google/guice) DI container and follows the canonical modular approach:

- framework provides a number of "core" modules
- user application can contribute additional features and override existing services by supplying a _**com.google.inject.Module**_

Core Bt modules are:

- [bt.module.ServiceModule](http://atomashpolskiy.github.io/bt/javadoc/latest/bt/module/ServiceModule.html)
- [bt.module.ProtocolModule](http://atomashpolskiy.github.io/bt/javadoc/latest/bt/module/ProtocolModule.html)
- [bt.peerexchange.PeerExchangeModule](http://atomashpolskiy.github.io/bt/javadoc/latest/bt/peerexchange/PeerExchangeModule.html)

By default only UDP trackers are supported by the core library. HTTP tracker integration is shipped as a standalone module in a separate Maven library:

- [bt.tracker.http.HttpTrackerModule](http://atomashpolskiy.github.io/bt/javadoc/latest/bt/tracker/http/HttpTrackerModule.html)

The reason for not including HTTP tracker support in the core is that it depends on [Apache HTTP Components](http://hc.apache.org/) library. Thus, if you plan on using HTTP tracker, include the following dependency in **pom.xml**:

```xml
<dependency>
    <groupId>com.github.atomashpolskiy</groupId>
    <artifactId>bt-http-tracker-client</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Then contribute _**bt.tracker.http.HttpTrackerModule**_ into runtime:

```java
BtRuntime runtime = BtRuntime.builder().module(new HttpTrackerModule()).build();
```

Peer exchange service is also turned off by default. Contribute _**bt.peerexchange.PeerExchangeModule**_ into runtime to enable peer sharing.

# **Configuration**

When it comes to configuration, Bt runtime provides reasonable defaults for most cases. However, you might still need to tweak some parameters, e.g. which network link to use, on which port to listen for incoming peer connections, etc. For such cases the runtime builder provides a handy method:

```java
Config config = new Config();
config.setAcceptorAddress(/* network address */);
config.setAcceptorPort(/* network port */);
// tweak other parameters

BtRuntime runtime = BtRuntime.builder(config).build();
```

See full list of configuration parameters on [Config](http://atomashpolskiy.github.io/bt/javadoc/latest/bt/runtime/Config.html) page in the JavaDoc.

# **Client lifecycle**

## _**Startup**_

There are two methods for starting the torrent session in [BtClient](http://atomashpolskiy.github.io/bt/javadoc/latest/bt/runtime/BtClient.html):

- [BtClient#startAsync()](http://atomashpolskiy.github.io/bt/javadoc/latest/bt/runtime/BtClient.html#startAsync--)

- [BtClient#startAsync(Consumer&lt;TorrentSessionState&gt;, long)](http://atomashpolskiy.github.io/bt/javadoc/latest/bt/runtime/BtClient.html#startAsync-java.util.function.Consumer-long-)

The no-argument method will just begin the torrent processing. The overloaded version will also launch a scheduled future, that will be calling the provided listener with current session state at a given interval. [Session state](http://atomashpolskiy.github.io/bt/javadoc/latest/bt/torrent/TorrentSessionState.html) contains some useful information, e.g. a list of connected peers, download progress and such, so it might be a good idea to inspect it from time to time. E.g. the [CLI client](https://github.com/atomashpolskiy/bt/tree/master/bt-cli) uses session state to determine whether it should stop:

```java
client.startAsync(state -> {
    if (!options.shouldSeedAfterDownloaded() && state.getPiecesRemaining() == 0) {
        client.stop();
    }
}, 1000).join();
```

Both methods return a [CompletableFuture&lt;Void&gt;](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html), which provides the most natural way for co-ordinating multiple torrent sessions. Run in parallel? Sequentially? Custom processing chain? All is possible via standard Java API.

## _**Shutdown**_

Stopping the client is as easy as calling [BtClient#stop()](http://atomashpolskiy.github.io/bt/javadoc/latest/bt/runtime/BtClient.html#stop--).

## _**Interconnection between runtime and its' clients**_

By default the runtime is configured to startup and shutdown synchronously with the client. 

This is not always the desired behaviour. E.g. when implementing a "pause" button, the client should be stopped and then started again after a user event. In such case creating and starting a new runtime each time the user clicks "resume" would be inefficient. That's why there is a dedicated method for turning this feature off:

- [BtRuntimeBuilder#disableAutomaticShutdown()](http://atomashpolskiy.github.io/bt/javadoc/latest/bt/runtime/BtRuntimeBuilder.html#disableAutomaticShutdown--).

Manual runtime shutdown is performed by calling [BtRuntime#shutdown()](http://atomashpolskiy.github.io/bt/javadoc/latest/bt/runtime/BtRuntime.html#shutdown--). Behaviour is completely identical to the automatic mode: stopping all clients (if any of them are still executing), performing registered shutdown hooks, releasing resources, done.

Everything stated above also applies when the runtime is shared among several clients (multiple simultaneous torrent sessions). With the only difference that this time runtime fires up when _any_ of the clients is started and shutdowns when _all_ of the clients are finished (unless automatic shutdown is disabled like in the single client case).