<h1 align="center">
    <a href="https://atomashpolskiy.github.io/bt/">Bt</a>
    <a href="https://twitter.com/intent/tweet?text=Bt%3A%20A%20Java%208%20BitTorrent%20library%20with%20DHT%2C%20magnet%20links%2C%20encryption%20and%20more&url=https://github.com/atomashpolskiy/bt&via=atomashpolskiy&hashtags=java,torrent,bittorrent,dht">
        <img src="https://img.shields.io/twitter/url/http/shields.io.svg?style=social&logo=twitter"
            alt="Tweet">
    </a>
</h1>

<p align="center"><strong>
<sup>
A full-featured BitTorrent implementation in Java 8
<br/><a href="http://bittorrent.org/beps/bep_0011.html">peer exchange</a> | <a href="http://bittorrent.org/beps/bep_0009.html">magnet links</a> | <a href="http://bittorrent.org/beps/bep_0005.html">DHT</a> | <a href="http://wiki.vuze.com/w/Message_Stream_Encryption">encryption</a> | <a href="http://bittorrent.org/beps/bep_0014.html">LSD</a> | <a href="http://bittorrent.org/beps/bep_0027.html">private trackers</a> | <a href="http://bittorrent.org/beps/bep_0010.html">extended protocol</a> | <a href="#partial-downloads">partial downloads</a> | <a href="https://en.wikipedia.org/wiki/Internet_Gateway_Device_Protocol">port forwarding</a>
</sup>
</strong></p>

<p align="center">
    <img src="https://atomashpolskiy.github.io/static/img/bt-cli-demo.gif" alt="Bt CLI">
</p>

<p align="center">
    <a href="https://travis-ci.com/github/atomashpolskiy/bt">
        <img src="https://travis-ci.com/atomashpolskiy/bt.svg?branch=master"
             alt="Linux build">
    </a>
    <a href="https://ci.appveyor.com/project/atomashpolskiy/bt">
        <img src="https://img.shields.io/appveyor/ci/atomashpolskiy/bt/master.svg?label=Windows%20build"
             alt="Linux build">
    </a>
    <a href="https://maven-badges.herokuapp.com/maven-central/com.github.atomashpolskiy/bt-core/">
        <img src="https://maven-badges.herokuapp.com/maven-central/com.github.atomashpolskiy/bt-core/badge.svg"
             alt="Maven Central">
    </a>
    <a href="https://atomashpolskiy.github.io/bt/javadoc/latest/">
        <img src="https://img.shields.io/badge/javadoc-latest-orange.svg"
             alt="JavaDoc">
    </a>
    <a href="https://codecov.io/gh/atomashpolskiy/bt">
        <img src="https://img.shields.io/codecov/c/github/atomashpolskiy/bt/master.svg"
             alt="Coverage">
    </a>
</p>

## Supported BEPs and extensions

* [BEP-3: The BitTorrent Protocol Specification](http://bittorrent.org/beps/bep_0003.html)
* [BEP-5: DHT Protocol](http://bittorrent.org/beps/bep_0005.html)
* [BEP-9: Extension for Peers to Send Metadata Files](http://bittorrent.org/beps/bep_0009.html)
* [BEP-10: Extension Protocol](http://bittorrent.org/beps/bep_0010.html)
* [BEP-11: Peer Exchange (PEX)](http://bittorrent.org/beps/bep_0011.html)
* [BEP-12: Multitracker metadata extension](http://bittorrent.org/beps/bep_0012.html)
* [BEP-14: Local Service Discovery](http://bittorrent.org/beps/bep_0014.html)
* [BEP-15: UDP Tracker Protocol](http://bittorrent.org/beps/bep_0015.html)
* [BEP-20: Peer ID Conventions](http://bittorrent.org/beps/bep_0020.html)
* [BEP-23: Tracker Returns Compact Peer Lists](http://bittorrent.org/beps/bep_0023.html)
* [BEP-27: Private Torrents](http://bittorrent.org/beps/bep_0027.html)
* [BEP-41: UDP Tracker Protocol Extensions](http://bittorrent.org/beps/bep_0041.html)
* [Message Stream Encryption](http://wiki.vuze.com/w/Message_Stream_Encryption)

## Resources

* **[HOME](https://atomashpolskiy.github.io/bt/)** – website with documentation and tutorials
* **[RELEASE NOTES](https://github.com/atomashpolskiy/bt/blob/master/RELEASE-NOTES.md#bt-release-notes)** – list of features, bugfixes and improments for each version
* **[UPGRADE INSTRUCTIONS](https://github.com/atomashpolskiy/bt/blob/master/UPGRADE.md#upgrade-instructions)** – version migration guide
* **[FORUM](https://groups.google.com/forum/#!forum/bttorrent)** – Google group for support and feedback
* **[TROUBLESHOOTING](#troubleshooting)** - solutions for some common problems
* **[LICENSE](https://github.com/atomashpolskiy/bt/blob/master/LICENSE)** – licensed under Apache License 2.0

## Runnable apps and demos

* **[CLI](https://github.com/atomashpolskiy/bt/tree/master/bt-cli#simple-command-line-torrent-downloader)** – command-line downloader
* **[PEER TRACKER](https://github.com/atomashpolskiy/bt/tree/master/examples/src/main/java/peertracker#peer-tracker)** – tracking of swarm statistics via events
* **[YOURIP MESSENGER](https://github.com/atomashpolskiy/bt/tree/master/examples/src/main/java/yourip#yourip-messenger)** – usage of custom messages

## Media

* **[HABRAHABR.RU: Пишем свой BitTorrent клиент на базе библиотеки Bt](https://habrahabr.ru/post/350076/)** - Introductory article and demonstration of basic capabilities (in Russian)
* **[SMARTSPATE.COM: How To Write Your Own BitTorrent Client By Using Bt Library](https://www.smartspate.com/how-to-write-your-own-bittorrent-client-by-using-bt-library/)** - English translation of the above article

## Prerequisites

<sup>Currently, all peer connections are established via [encryption negotation protocol](http://wiki.vuze.com/w/Message_Stream_Encryption) (also called MSE handshake). If you're using Oracle JDK ([pre 8u152](http://www.oracle.com/technetwork/java/javase/8u152-relnotes-3850503.html)), in order to be able to connect to peers you must install [Java Cryptography Extension (JCE) Unlimited Strength Jurisdiction Policy](http://www.oracle.com/technetwork/java/javase/downloads/jce8-download-2133166.html). The reason for this requirement is that the [MSE RC4 cipher](http://wiki.vuze.com/w/Message_Stream_Encryption) uses 160 bit keys, while default Java installation allows at most 128 bit keys.</sup>

## Usage

Most recent version available in Maven Central is **1.10**.

Declare the following dependencies in your project’s **pom.xml**:

```xml
<dependency>
    <groupId>com.github.atomashpolskiy</groupId>
    <artifactId>bt-core</artifactId>
    <version>${bt-version}</version>
</dependency>
<dependency>
    <groupId>com.github.atomashpolskiy</groupId>
    <artifactId>bt-http-tracker-client</artifactId>
    <version>${bt-version}</version>
</dependency>
<dependency>
    <groupId>com.github.atomashpolskiy</groupId>
    <artifactId>bt-dht</artifactId>
    <version>${bt-version}</version>
</dependency>
<dependency>
    <groupId>com.github.atomashpolskiy</groupId>
    <artifactId>bt-upnp</artifactId>
    <version>${bt-version}</version>
</dependency>
```

## Building from source

```
git clone https://github.com/atomashpolskiy/bt.git
cd bt
mvn clean install -DskipTests
```

## Code sample

### Download a torrent from a magnet link
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

// get download directory
Path targetDirectory = Paths.get(System.getProperty("user.home"), "Downloads");

// create file system based backend for torrent data
Storage storage = new FileSystemStorage(targetDirectory);

// create client with a private runtime
BtClient client = Bt.client()
        .config(config)
        .storage(storage)
        .magnet("magnet:?xt=urn:btih:af0d9aa01a9ae123a73802cfa58ccaf355eb19f1")
        .autoLoadModules()
        .module(dhtModule)
        .stopWhenDownloaded()
        .build();

// launch
client.startAsync().join();
```
### Create a torrent
```java
Path torrentRoot = Paths.get("/home/torrents/mytorrent");
Path file1 = Paths.get("/home/torrents/mytorrent/file1.bin");
Path file2 = Paths.get("/home/torrents/mytorrent/file2.bin");
Path dirToAdd = Paths.get("/home/torrents/mytorrent/dir_with_files");
byte[] torrentBytes = new TorrentBuilder()
        .rootPath(torrentRoot)
        .addFiles(file1, file2, dirToAdd)
        .announce("http://example.com/announce")
        .build();
Files.write(Paths.get("/home/torrents/mytorrent.torrent"), torrentBytes);
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

**Bt** has out-of-the-box support for multiple simultaneous torrent sessions with minimal system overhead.

### Partial downloads

**Bt** has an API for selecting only a subset of torrent files to download. See the `bt.TorrentClientBuilder.fileSelector(TorrentFileSelector)` client builder method. File selection works for both `.torrent` file-based and magnet link downloads.

### Java 8 CompletableFuture

Client API leverages the asynchronous `java.util.concurrent.CompletableFuture` to provide the most natural way for co-ordinating multiple torrent sessions. E.g. use `CompletableFuture.allOf(client1.startAsync(...), client2.startAsync(...), ...).join()`. Or create a more sophisticated processing pipeline.

### And much more...

* _**check out [Release Notes](https://github.com/atomashpolskiy/bt/blob/master/RELEASE-NOTES.md#bt-release-notes) for details!**_

## Troubleshooting

### Can't connect to peers, everything else seems to work

If you're using an Oracle JDK, make sure that you have installed [Java Cryptography Extension (JCE) Unlimited Strength Jurisdiction Policy](http://www.oracle.com/technetwork/java/javase/downloads/jce8-download-2133166.html).

### Other BitTorrent clients can't connect to a Bt client / No incoming connections when seeding

a) If you're behind a firewall and/or a NAT (e.g. a router), make sure they are configured to allow incoming TCP and UDP connections on the ports used by Bt. Default Bt ports are 6891 and 49001 for BitTorrent and DHT respectively. NAT must additionally be configured to forward all incoming traffic on these ports to the host, that Bt is running on.

b) Many popular BitTorrent clients use UPnP and NAT-PMP to automatically configure port forwarding on NATs. Since 1.8 Bt does this as well, so make sure to include [bt-upnp](https://github.com/atomashpolskiy/bt/tree/master/bt-upnp) module in the list of dependencies.

### There are exceptions in the build log (but the build completes successfully)

This is perfectly fine. Some of the tests verify that the exceptions are thrown in certain cases, hence the exception messages.

### Can't run the CLI on Windows XP (java.io.IOException: Cannot run program "/bin/stty")

CLI GUI indeed does not work on Windows XP. Run in headless mode by using `-H` flag.

### Can't connect to peers on Windows 7/8/10

There seem to be some issues with dual-stack networking in Windows JDK (e.g. see [this question on SO](https://superuser.com/questions/453298/how-to-force-java-to-use-ipv4-instead-ipv6)), with Java trying to use IPv6 address, when it's not really available in the system. The simplest solution is to force Java to use IPv4 by setting `java.net.preferIPv4Stack` property to `true`.

## Support and feedback

Any thoughts, ideas, criticism, etc. are welcome, as well as votes for new features and BEPs to be added. You have the following options to share your ideas, receive help or report bugs:

* open a new [GitHub issue](https://github.com/atomashpolskiy/bt/issues)
* post your question on the [Bt forum](https://groups.google.com/forum/#!forum/bttorrent)

## Donate

<p align="left">
    <img src="https://atomashpolskiy.github.io/static/img/bt-donate-btc-qr.png" width="200px" alt="Donate">
</p>
