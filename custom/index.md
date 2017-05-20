---
layout: page
title: IoC and Customization
permalink: /custom/
---

# **IoC container**

One of the primary goals of Bt design is to give the user the power to extend it and customize it to her own liking. It uses a popular IoC container [Google Guice](github.com/google/guice) and follows a modular approach, in which different components are loosely coupled and interact via generic interfaces. 

The idea is that an advanced user who is familiar with the concept of DI should be able to easily navigate the codebase (which is pretty well-documented with JavaDoc) and provide custom services by standard means that Guice provides. 

However, reality is such that the need to explore library internals can be a daunting task for a new user. Hence, there is a number of "standard" extension points that will help you get started.

# **Static contribution methods**

The idea of contribution methods is borrowed from [Bootique](github.com/bootique/bootique) project and is very well explained in [this DZone article](https://dzone.com/articles/guice-stories-part-1).

In short, contribution method is a static method in a Guice `Module`, that accepts a `Binder` - primary DI tool for wiring interface classes to their implementations - and returns a `Multibinder` or a `MapBinder`, which are then used to contribute custom classes into the core.
 
 In Bt one notable example of such method is `bt.module.ProtocolModule#contributeExtendedMessageHandler`. By using this method you can utilize the official [Extension Protocol](http://www.bittorrent.org/beps/bep_0010.html) to build custom messaging. There is a dedicated page about [using Extension Protocol](../extension-protocol), where you'll be able to find more information.
 
 Other noteworthy contribution methods are:
 
 * `bt.module.ServiceModule#contributePeerSourceFactory` to provide custom sources of peers
 * `bt.module.ServiceModule#contributeMessagingAgent` to register agents that will receive and produce BitTorrent messages (both standard and custom)
 * `bt.module.ServiceModule#contributeTrackerFactory` to add or replace tracker clients for different protocols (http:// and udp:// are already there), not necessarily real ones, something like torrent:// will also work
 * `bt.module.ProtocolModule#contributeMessageHandler` to extend the standard protocol itself (there are only 11 types of messages in BitTorrent, why not add more?)
 * `bt.module.ProtocolModule#contributeHandshakeHandler` to register a handler that will be called when performing handshake with a new peer; this will allow you to create custom rules for establishing peer connections
