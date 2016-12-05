---
layout: page
title: A Brief Introduction
permalink: /intro/
---

# **Setup**

## _**Maven**_

Bt 1.0 is going to be released and published to Maven Central in the nearest time. In the meanwhile you may download and build it manually from github:

```bash
git clone https://github.com/atomashpolskiy/bt.git
cd bt
mvn -DskipTests=true clean install
```

This will package Bt artifacts and install them to your local .m2 repository. Then declare the following dependencies in your project's **pom.xml**:

```xml
<dependency>
    <groupId>com.github.atomashpolskiy</groupId>
    <artifactId>bt-core</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>com.github.atomashpolskiy</groupId>
    <artifactId>bt-http-tracker-client</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

# **Command-line launcher**
 
Bt CLI client is a very simple program for downloading/seeding a single torrent. It illustrates the most basic use case of Bt library.

See [usage notes](https://github.com/atomashpolskiy/bt/tree/master/bt-cli) and explore the [CliClient#runWithOptions()](https://github.com/atomashpolskiy/bt/blob/master/bt-cli/src/main/java/bt/cli/CliClient.java) method for an example of assembling a basic Bt client.

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
