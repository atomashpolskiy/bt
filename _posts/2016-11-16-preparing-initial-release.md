---
layout: post
title: Approaching initial release
---


I've been working on **Bt** for more than half a year now, and I'm delighted to say that it's gradually approaching its' first official release. There are still a couple of things left, mostly related to API stabilization and overall cleanup, as well as creating some kind of documentation. Though it's a merely mechanical work for the most part, so the future looks bright to me.

Lyrics aside, today I'd like to outline the scope for the upcoming release. Everything listed below is already finished and working, so it should not be viewed as a plan, but rather a _**checklist**_ of stuff for me to verify and include into the final assembly.

That's all for now, stay tuned for further updates!

## Release 1.0 Scope

- Bencoding parser/encoder
- Validation of arbitrary bencoded documents according to user-provided object models and YAML schemas
- Filesystem-based data back-end, support for providing custom data back-ends
- URL metainfo fetcher, support for providing custom metainfo fetchers
- HTTP tracker integration
- Multi-trackers support
- Private tracker support
- Standard bittorrent protocol and messaging
- Full support for protocol extensions, including customization of handshake procedure
- Support for providing custom peer sources
- Peer exchange (protocol for p2p exchange of known peers in the swarm)
- Support for custom messaging agents (consumers and producers), both for standard and extended protocols
- Sequential, rarest-first and randomized rarest-first piece selection strategies
- Shared runtime with multiple simultaneous torrent sessions
- Test infrastructure with support for launching peer swarm on localhost to test new features in a real environment

### List of included BEPs

- <a href="http://bittorrent.org/beps/bep_0003.html">BEP-3: The BitTorrent Protocol Specification</a>
- <a href="http://bittorrent.org/beps/bep_0010.html">BEP-10: Extension Protocol</a>
- <a href="http://bittorrent.org/beps/bep_0011.html">BEP-11: Peer Exchange (PEX)</a>
- <a href="http://bittorrent.org/beps/bep_0012.html">BEP-12: Multitracker metadata extension</a>
- <a href="http://bittorrent.org/beps/bep_0020.html">BEP-20: Peer ID Conventions</a>
- <a href="http://bittorrent.org/beps/bep_0023.html">BEP-23: Tracker Returns Compact Peer Lists</a>
- <a href="http://bittorrent.org/beps/bep_0027.html">BEP-27: Private Torrents</a>