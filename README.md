[![Build Status](https://travis-ci.org/atomashpolskiy/bt.svg?branch=master)](https://travis-ci.org/atomashpolskiy/bt) [![codecov](https://img.shields.io/codecov/c/github/atomashpolskiy/bt/master.svg)](https://codecov.io/gh/atomashpolskiy/bt) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.atomashpolskiy/bt-core/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.atomashpolskiy/bt-core/) [![Javadoc](https://img.shields.io/badge/javadoc-latest-blue.svg)](http://atomashpolskiy.github.io/bt/javadoc/latest/)

**Bt** is a modern full-featured BitTorrent library in Java 8. It offers good performance, reliability and is highly customizable. Bt is still in its' early days, but is actively developed and designed with stability and maintainability in mind.

## Quick Links

[Website](http://atomashpolskiy.github.io/bt/) (contains examples and tutorials)

[JavaDoc](http://atomashpolskiy.github.io/bt/javadoc/latest/) (based on the latest commit in _**master**_)

[CLI Launcher](https://github.com/atomashpolskiy/bt/tree/master/bt-cli)

## List of supported BEPs

* [BEP-3: The BitTorrent Protocol Specification](http://bittorrent.org/beps/bep_0003.html)
* [BEP-5: DHT Protocol](http://bittorrent.org/beps/bep_0005.html)
* [BEP-10: Extension Protocol](http://bittorrent.org/beps/bep_0010.html)
* [BEP-11: Peer Exchange (PEX)](http://bittorrent.org/beps/bep_0011.html)
* [BEP-12: Multitracker metadata extension](http://bittorrent.org/beps/bep_0012.html)
* [BEP-15: UDP Tracker Protocol](http://bittorrent.org/beps/bep_0015.html)
* [BEP-20: Peer ID Conventions](http://bittorrent.org/beps/bep_0020.html)
* [BEP-23: Tracker Returns Compact Peer Lists](http://bittorrent.org/beps/bep_0023.html)
* [BEP-27: Private Torrents](http://bittorrent.org/beps/bep_0027.html)
* [BEP-41: UDP Tracker Protocol Extensions](http://bittorrent.org/beps/bep_0041.html)
* [Message Stream Encryption](http://wiki.vuze.com/w/Message_Stream_Encryption)

## Building from source

1) Clone:
```
git clone https://github.com/atomashpolskiy/bt.git
```

2) Build:
```
cd bt
mvn clean install -DskipTests=true
 ```
 
3) Download with [CLI launcher](https://github.com/atomashpolskiy/bt/tree/master/bt-cli) or use as a library:
```
java -Xmx64m -jar bt-cli/target/bt-launcher.jar -f <path-to-torrent-file> -d <download-dir>
```

## Usage

Most recent version available in Maven Central is **1.2**. 

DHT module is available in **1.2** git tag (compatible with Bt 1.2 version) or **1.3-SNAPSHOT** (current master version), either of which should be built manually. 

Declare the following dependencies in your project’s **pom.xml**:

```xml
<dependency>
    <groupId>com.github.atomashpolskiy</groupId>
    <artifactId>bt-core</artifactId>
    <version>${bt-version}</version>
</dependency>
<!-- for the sake of keeping the core with minimum number of 3-rd party 
     dependencies HTTP tracker support is shipped as a separate module;
     you may omit this dependency if only UDP trackers are going to be used -->
<dependency>
    <groupId>com.github.atomashpolskiy</groupId>
    <artifactId>bt-http-tracker-client</artifactId>
    <version>${bt-version}</version>
</dependency>
<!-- bt-dht will be available if you've built the project manually -->
<dependency>
    <groupId>com.github.atomashpolskiy</groupId>
    <artifactId>bt-dht</artifactId>
    <version>${bt-version}</version>
</dependency>
```

## Code sample

```java
// enable multithreaded verification of torrent data
Config config = new Config() {
    @Override
    public int getNumOfHashingThreads() {
        return Runtime.getRuntime().availableProcessors() * 2;
    }
};

// enable bootstrapping from public routers
Module dhtModule = new DHTModule(new DHTConfig() {
    @Override
    public boolean shouldUseRouterBootstrap() {
        return true;
    }
});

// get torrent file URL and download directory
URL torrentUrl = getTorrentUrl();
File targetDirectory = getTargetDirectory();

// create file system based backend for torrent data
Storage storage = new FileSystemStorage(targetDirectory);

// create client with a private runtime
BtClient client = Bt.client()
        .config(config)
        .storage(storage)
        .torrent(torrentUrl)
        .autoLoadModules()
        .module(dhtModule)
        .build();

// launch
client.startAsync(state -> {
    if (state.getPiecesRemaining() == 0) {
        client.stop();
    }
}, 1000).join();
```

## What makes Bt stand out from the crowd

### Flexibility

Being built around the [Guice](https://github.com/google/guice) DI, **Bt** provides many options for tailoring the system for your specific needs. If something is a part of Bt, then it can be modified or substituted for your custom code.

### Custom backends

**Bt** is shipped with a standard file-system based backend (i.e. you can download the torrent file to a storage device). However, the backend details are abstracted from the message-level code. This means that you can use your own backend by providing a _storage unit_ implementation.

### Protocol extensions

One notable customization scenario is extending the standard BitTorrent protocol with your own messages. BitTorrent's [BEP-10](http://www.bittorrent.org/beps/bep_0010.html) provides a native support for protocol extensions, and implementation of this standard is already included in **Bt**. Contribute your own _Messages_, byte manipulating _MessageHandlers_, message _consumers_ and _producers_; supply any additional info in _ExtendedHandshake_.

### Test infrastructure

To allow you test the changes that you've made to the core, **Bt** ships with a specialized framework for integration tests. Create an arbitrary-sized _swarm_ of peers inside a simple _JUnit_ test, set the number of seeders and leechers and start a real torrent session on your localhost. E.g. create one seeder and many leechers to stress test the network overhead; use a really large file and multiple peers to stress test your newest laptop's expensive SSD storage; or just launch the whole swarm in _no-files_ mode and test your protocol extensions.

### Parallel downloads

**Bt** has out-of-the-box support for multiple simultaneous torrent sessions with minimal system overhead. 1% CPU and 32M of RAM should be enough for everyone!

### Java 8 CompletableFuture

Client API leverages the asynchronous `java.util.concurrent.CompletableFuture` to provide the most natural way for co-ordinating multiple torrent sessions. E.g. use `CompletableFuture.allOf(client1.startAsync(...), client2.startAsync(...), ...).join()`. Or create a more sophisticated processing pipeline.

### And much more...

* _**check out [Release Notes](https://github.com/atomashpolskiy/bt/blob/master/RELEASE-NOTES.txt) for details!**_

## Support and feedback

Any thoughts, ideas, criticism, etc. are welcome, as well as votes for new features and BEPs to be added. You have the following options to share your ideas, receive help or report bugs:

* open a new  [GitHub issue](https://github.com/atomashpolskiy/bt/issues)
* post your question on the [Bt forum](https://groups.google.com/forum/#!forum/bttorrent)