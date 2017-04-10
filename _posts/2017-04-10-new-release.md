---
layout: post
title: Release 1.1
---


Release 1.1 was published to Maven Central on April 10th, 2017. It includes a number of major performance and algorithmic improvements, critical bug fixes and API enhancements. **It is strongly recommended for all users to switch to the new version.**

### Changes/New Features:

* Support for auto-loading modules from the classpath
* Enhanced API for building standalone and shared-runtime clients
* Streaming (continuous) piece selectors
* Improvements in piece selection and peer assignments algorithm
* Support for multi-threaded hashing (verification) of torrent data on startup
* Lifecycle binding API improvements; support for asynchronous bindings
* Tools for creating custom protocol tests

### Bug Fixes/Improvements:

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
