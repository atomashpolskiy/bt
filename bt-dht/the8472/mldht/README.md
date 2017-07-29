# mldht

[![Build Status](https://travis-ci.org/the8472/mldht.svg?branch=master)](https://travis-ci.org/the8472/mldht)

A java library and standalone node implementing the Kademlia-based bittorrent mainline DHT, with long-running server-class nodes in mind.

Originally developed as [DHT plugin](http://azsmrc.sourceforge.net/index.php?action=plugin-mldht) for Azureus/[Vuze](http://dev.vuze.com/)

## Features

Implemented specs:

| Spec | Title | Status |
|------|-------|--------|
|[BEP5](http://bittorrent.org/beps/bep_0005.html)|Bittorrent DHT| Yes |
|[BEP32](http://bittorrent.org/beps/bep_0032.html)|IPv6| Yes |
|[BEP33](http://bittorrent.org/beps/bep_0033.html)|Scrapes| Yes |
|[BEP42](http://bittorrent.org/beps/bep_0042.html)|DHT Announce Security| Partial; only the `ip` fields for external address discovery are supported |
|[BEP9](http://bittorrent.org/beps/bep_0009.html)|Metadata exchange| Partial; only fetching is supported |
|[libtorrent.org](http://www.libtorrent.org/dht_extensions.html)| Extended `get_peers` response<br> Forward compatibility<br> Client identification|Yes|
|[BEP45](http://bittorrent.org/beps/bep_0045.html)|multi-homing/multi-address mode|Yes|
|[BEP44](http://bittorrent.org/beps/bep_0044.html)|Arbitrary data storage|Yes|
|[BEP50](http://bittorrent.org/beps/bep_0050.html)|Pub/Sub|No|
|[BEP51](http://bittorrent.org/beps/bep_0051.html)|DHT Infohash Indexing|Yes| 

Additional:

- high-performance implementation without compromising correctness, i.e. the node will be a good citizen
 - can process 20k packets per second on a single Xeon core
- low latency lookups by using adaptive timeouts and a secondary routing table/cache tuned for RTT instead of stability
- export of passively observed \<infohash, ip\> tuples to redis to survey torrent activity
- remote CLI for common DHT operations
- full automatic torrent indexing (active and passive dht indexing + metadata exchange)

## Dependencies

- java 8
- maven 3.1 (building)

installed via maven:

- ed25519-java
- junit 4.x (tests)

## build

    git clone https://github.com/the8472/mldht.git .
    mvn package dependency:copy-dependencies appassembler:assemble
    # install symlink scripts to ~/bin/ 
    mvn antrun:run@link
    
## embedding as library

See [docs/use-as-library.md](docs/use-as-library.md) for further information.

## run in standalone mode

    mkdir -p work
    cd work
    ../bin/mldht-daemon
    # or manually
    # java -cp "../target/*:../target/dependency/*" the8472.mldht.Launcher &
    
this will create various files in the current working directory
- `config.xml`, change settings as needed, core settings will be picked up on file modification
- `shutdown`, touch to cleanly shutdown running process (SIGHUP works too)
- `*-table.cache`, persisted routing table for the ipv4/6 dhts, respectively
- `baseID.config`, persisted node ID
- `logs/*`, various diagnostics and log files
- `.keys/`, default storage directory for BEP44 private keys. used by the CLI

**Security note:** the shell script launches the JVM with a debug port bound to localhost for easier maintenance, thus allowing arbitrary code execution with the current user's permissions. In a multi-user environment a custom script with debugging disabled should be used    


## network configuration

* stateful NATs or firewalls should be put into stateless mode/disable connection tracking and use static forwarding rules for the configured local ports [default: 49001].<br>Otherwise state table overflows may occur which lead to dropped packets and degraded performance.
* nat/firewall rules should not assume any particular remote port, as other DHT nodes are free to chose their own.
* If no publicly routable IPv6 address is available then IPv6 should be disabled
* If only NATed IPv4 addresses are available then multihoming mode should be disabled
* The length of network interface send queues should be increased when the DHT node is operated in multihoming mode on a server with many public IPs.<br>This is necessary because UDP sends may be silently dropped when the send queue is full and DHT traffic can be very bursty, easily saturating too-small queues<br>Check system logs or netstat statistics to see if outgoing packets are dropped.
* For similar reasons the maximum socket receive buffer size should be set to at least 2MB, which is the amount this implementation will request when configuring its sockets


## optional components

Some features are not enabled out of the box because they only require external infrastructure, provide public services or would cause extra traffic.

They are enabled by adding or uncommenting a `<component><className>...</className></component>` entry to the config.xml 


* `the8472.mldht.cli.Server` to enable the remote CLI
* `the8472.mldht.indexing.TorrentDumper` obtains infohashes from incoming traffic, then does all the necessary work to fetch them. can acquire approximately 0.3 torrents per second on a single-homed setup without firewall.
* `the8472.mldht.indexing.ActiveLookupProvider` raw TCP interface for requesting DHT scrapes on port 36578. just send infohashes in hex, newline separated
* `the8472.mldht.indexing.OpentrackerLiveSync` implements a lan-local multicast sender for opentracker's IPv4 live sync. for passively observed DHT lookups will be inserted as peers in opentracker instance. opentracker instance can then be used as source for DHT statistics as if it were just another tracker
* `the8472.mldht.PassiveRedisIndexer` obtains statistics on peers seen on particular infohashes

### remote-cli

launch daemon with

```xml
    <component>
      <className>the8472.mldht.cli.Server</className>
    </component>
```

run CLI client with

```
bin/mldht-remote-cli help
# or manually:
# java -cp "target/*" the8472.mldht.cli.Client help
```

available commands (subject to change):

```
HELP                                                 - prints this help
PING ip port                                         - continuously pings a DHT node with a 1 second interval
GET hash [salt]                                      - perform a BEP44 get
PUT -f <input-path> [-keyfile <path>] [-salt <salt>]
PUT <input> [-keyfile <path>] [-salt <salt>]         - perform a BEP44 put, specifying a salt or keyfile implies a mutable put, immutable otherwise. data will be read from file or as single argument
GETTORRENT [infohash...]                             - peer lookup for <infohash(es)>, then attempt metadata exchange, then write .torrent file(s) to the current working directory
GETPEERS [infohash...] [-fast]                       - peer lookup for <infohash(es)>, print ip address/port tuples
BURST [count]                                        - run a batch of find_node lookups to random target IDs. intended test the attainable throughput for active lookups, subject to internal throttling
```


**Security note:** The CLI Server component listens on localhost, accepting commands without authentication from any user on the system. It is recommended to not use this component in a multi-user environment.



### redis statistics export


```xml
    <component xsi:type="mldht:redisIndexerType">
      <className>the8472.mldht.PassiveRedisIndexer</className>
      <address>127.0.0.1</address><!-- additional parameter to allow exporting to other hosts -->
    </component>
```


### custom components

Simply implement [<tt>Component</tt>](src/the8472/mldht/Component.java) and configure the launcher to include it on startup through the config.xml:
	
```xml
    <component>
      <className>your.class.name.Here</className>
    </component>
```