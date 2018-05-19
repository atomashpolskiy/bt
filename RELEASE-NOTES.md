# Bt Release Notes

For the latest information visit project web site: http://atomashpolskiy.github.io/bt/

## 1.8

#### Date:

### New Features:

* UPnP port mapping [#80](https://github.com/atomashpolskiy/bt/issues/80)

## 1.7

#### Date: 02/26/2018

#### Changes/New Features:

* Support for file selection (aka partial downloads)

#### Bug Fixes/Improvements:

* Avoid creation of unnessary empty dirs when reading from a FileSystemStorageUnit that maps to an absent file
* BEncoder: sort dictionary keys as raw byte sequences, not alphanumerical strings #50
* Randomized rarest-first selector behaves like a sequential selector when peers are seeds #53
* Empty files should not prevent successful verification of torrent's data
* NPE in DefaultChannelPipeline when there are unprocessed leftovers from MSE handshake #57
* Incorrect behavior when the same peer participates in more than one torrent #67

## 1.6

#### Date: 01/27/2018

#### Official BEPs:

* [BEP-14: Local Service Discovery](http://www.bittorrent.org/beps/bep_0014.html)

#### Bug Fixes/Improvements:

* PeerTracker example does not work on Windows
* Allow to selectively enable only a subset of standard extensions, like PEX and LSD
* Re-use native memory messaging buffers between different peer connections
* Check the allowed crypto key size and disable MSE if insufficient [#24](https://github.com/atomashpolskiy/bt/issues/24)

## 1.5

#### Date: 09/26/2017

#### Changes/New Features:

* Introduce a unified, centralized mechanism for publishing/receiving events
* Introduce a processing stage listener mechanism

#### Bug Fixes/Improvements:

* Disallow to set client's runtime other than via Bt factory method
* Introduce module extenders for contributing custom extensions
* Disable BEP-9 metadata exchange for private torrents
* DefaultClient state fix when client is stopped [PR#37](https://github.com/atomashpolskiy/bt/pull/37)
* Announce stats to tracker on start, stop, complete
* Use I/O selector for receiving incoming messages
* Allow to override the number of peers to request from a tracker
* Provide information on creation date and creator of the torrent
* Support empty files

## 1.4.1

#### Date: 08/20/2017

#### Bug Fixes:

* java.lang.IllegalAccessError when instantiating StandaloneClientBuilder from Scala [PR#36](https://github.com/atomashpolskiy/bt/pull/36)

## 1.4

#### Date: 08/14/2017

#### Changes/New Features:

* Choose a specific network interface [#20](https://github.com/atomashpolskiy/bt/issues/20)

##### Bug Fixes/Improvements:

* Use generic java.nio.files interfaces in FileSystemStorage [#21](https://github.com/atomashpolskiy/bt/issues/21) by [Jeremy L. Morris (MorrisLaw)](https://github.com/MorrisLaw)
* Switch integration tests to using in-memory storage [#27](https://github.com/atomashpolskiy/bt/issues/27)
* UDP tracker request contains 0 as the listening port
* Download not starting when using standalone client with private runtime [#34](https://github.com/atomashpolskiy/bt/issues/34)

## 1.3.1

#### Date: 08/11/2017

#### Bug Fixes:

* java.lang.IllegalAccessError when running inside JBoss modules [#32](https://github.com/atomashpolskiy/bt/issues/32)

## 1.3

#### Date: 07/29/2017

#### Official BEPs:

* [BEP-5: DHT Protocol](http://bittorrent.org/beps/bep_0005.html)
* [BEP-9: Extension for Peers to Send Metadata Files](http://bittorrent.org/beps/bep_0009.html)

#### Changes/New Features:

* Added ByteRange for working with binary ranges based on byte arrays and byte buffers
* Support creating torrents from binary representation of info dictionary

#### Bug Fixes/Improvements:

* Introduced notion of torrent processing chain
* Fixed bug in extended protocol (invalid message type id mapping for peers) that sometimes prevented peers from receiving extended messages from Bt
* Reduced dependency on the presence of a torrent; using torrent ID where possible
* Perform peers lookup for active torrents only
* Support HTTPS trackers
* Configurable list of bootstrap DHT nodes
* Configurable MSE private key size
* Headless mode in CLI client (Windows compatibility)
* Fix for occasional UI crashes in CLI client
* Allow to specify the desired log level in CLI client (normal, verbose, trace)

## 1.2

#### Date: 05/24/2017

#### Changes/New Features:

* [Message Stream Encryption](http://wiki.vuze.com/w/Message_Stream_Encryption)
* Added API for retrieving the full list of registered torrents

#### Bug Fixes/Improvements:

* Last block in a chunk is incorrectly marked as complete even when partially written
* Provide info on encryption support, local TCP port and version in extended handshake
* Eliminate self-connections in tests
* Don't specify the recipient of a PEX message in the list of added peers

## 1.1

#### Date: 04/10/2017

#### Changes/New Features:

* Support for auto-loading modules from the classpath
* Enhanced API for building standalone and shared-runtime clients
* Streaming (continuous) piece selectors
* Improvements in piece selection and peer assignments algorithm
* Support for multi-threaded hashing (verification) of torrent data on startup
* Lifecycle binding API improvements; support for asynchronous bindings
* Tools for creating custom protocol tests

#### Bug Fixes/Improvements:

* Torrent processing should not terminate when interaction with the tracker failed
* Announce key can be missing in trackerless torrents
* Failures on receiving unexpected blocks should be optional
* Peer connection occasionally stopped receiving/sending data due to a buffer compaction bug
* Verification tasks should be submitted only for complete pieces
* Chunk descriptor overlapping two files contained no blocks when the latter file was smaller than the leftovers from the former file
* Calculate total size for multi-file torrents
* NPE on UDP message worker shutdown
* Multi-tracker does not announce to next trackers in tier if an exception was thrown
* Querying trackers and other peer sources should be async
* Adaptive message processing interval in message dispatcher to reduce the CPU load
* Speed-up the initial startup by skipping verification if a storage unit is empty and by feeding larger blocks to the digester

## 1.0

#### Date: 01/16/17

#### Features:

* Bencoding parser/encoder
* Validation of arbitrary bencoded documents according to user-provided object models and YAML schemas
* Filesystem-based data back-end, support for providing custom data back-ends
* URL metainfo fetcher, support for providing custom metainfo fetchers
* HTTP and UDP tracker integration
* Multi-tracker support
* Private tracker support
* Standard bittorrent protocol and messaging
* Full support for protocol extensions, including customization of handshake procedure
* Support for providing custom peer sources
* Peer exchange (protocol for p2p exchange of known peers in the swarm)
* Support for custom messaging agents (consumers and producers), both for standard and extended protocols
* Sequential, rarest-first and randomized rarest-first piece selection strategies
* Shared runtime with multiple simultaneous torrent sessions
* Test infrastructure with support for launching peer swarm on localhost to test new features in a real environment

#### Official BEPs:

* [BEP-3: The BitTorrent Protocol Specification](http://bittorrent.org/beps/bep_0003.html)
* [BEP-10: Extension Protocol](http://bittorrent.org/beps/bep_0010.html)
* [BEP-11: Peer Exchange (PEX)](http://bittorrent.org/beps/bep_0011.html)
* [BEP-12: Multitracker metadata extension](http://bittorrent.org/beps/bep_0012.html)
* [BEP-15: UDP Tracker Protocol](http://bittorrent.org/beps/bep_0015.html)
* [BEP-20: Peer ID Conventions](http://bittorrent.org/beps/bep_0020.html)
* [BEP-23: Tracker Returns Compact Peer Lists](http://bittorrent.org/beps/bep_0023.html)
* [BEP-27: Private Torrents](http://bittorrent.org/beps/bep_0027.html)
* [BEP-41: UDP Tracker Protocol Extensions](http://bittorrent.org/beps/bep_0041.html)