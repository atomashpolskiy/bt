---
layout: page
title: Extension protocol
permalink: /extension-protocol/
order: 2
---

[Extension Protocol](http://www.bittorrent.org/beps/bep_0010.html) is the preferred way of building custom messaging on top of BitTorrent. There is a bunch of well-known messages: `ut_pex` ([Peer Exchange](http://www.bittorrent.org/beps/bep_0011.html)), `ut_metadata` ([magnet: links](http://www.bittorrent.org/beps/bep_0009.html)), and others. Bt provides a simple way to utilize the same mechanism to create custom protocols.

# **Building YourIP messenger**

In this tutorial we are going to create a simple extension that will let peers know what their external IP address looks like (or how this peer is seen by the others). In real BitTorrent this information is provided during the extended handshake procedure. 

Source code and runnable program are available in [Bt examples](https://github.com/atomashpolskiy/bt/tree/master/examples/src/main/java/yourip).

First, let's design the message itself. It must be a subtype of `ExtendedMessage` to be recognized by the extension protocol. A helper method `writeTo(OutputStream)` will take care of transforming the message object into bytes.

```java
package yourip;

import bt.bencoding.model.BEMap;
import bt.bencoding.model.BEObject;
import bt.bencoding.model.BEString;
import bt.protocol.extended.ExtendedMessage;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.HashMap;

public class YourIP extends ExtendedMessage {

    private static final String id = "yourip";
    private static final String addressField = "address";

    public static String id() {
        return id;
    }

    public static String addressField() {
        return addressField;
    }

    private final String address;

    public YourIP(String address) {
        this.address = address;
    }

    public String getAddress() {
        return address;
    }

    void writeTo(OutputStream out) throws IOException {
        byte[] bytes = address.getBytes(Charset.forName("UTF-8"));
        BEMap message = new BEMap(null, new HashMap<String, BEObject<?>>() { {
            put(addressField, new BEString(bytes));
        }});
        message.writeTo(out);
    }
}
```

Second, we need a handler that will perform serialization of a message into a binary form and deserialization of a binary representation into an object. 

```java
package yourip;

import bt.bencoding.BEParser;
import bt.bencoding.model.BEMap;
import bt.protocol.DecodingContext;
import bt.protocol.handler.MessageHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;

public class YourIPMessageHandler implements MessageHandler<YourIP> {

    @Override
    public boolean encode(YourIP message, ByteBuffer buffer) {
        boolean encoded = false;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            message.writeTo(bos);
            byte[] payload = bos.toByteArray();
            if (buffer.remaining() >= payload.length) {
                buffer.put(payload);
                encoded = true;
            }
        } catch (IOException e) {
            // can't happen
        }
        return encoded;
    }

    @Override
    public int decode(DecodingContext context, ByteBuffer buffer) {
        byte[] payload = new byte[buffer.remaining()];
        buffer.get(payload);
        try (BEParser parser = new BEParser(payload)) {
            BEMap message = parser.readMap();
            String address = new String(
                message.getValue().get(YourIP.addressField()).getContent(), 
                Charset.forName("UTF-8"));

            context.setMessage(new YourIP(address));
            return message.getContent().length;
        }
    }

    @Override
    public Collection<Class<? extends YourIP>> getSupportedTypes() {
        return Collections.singleton(YourIP.class);
    }

    @Override
    public Class<? extends YourIP> readMessageType(ByteBuffer buffer) {
        return YourIP.class;
    }
}
```

Because our message already knows how to serialize itself, the `encode(Message, ByteBuffer)` method will only be responsible for checking that the I/O buffer has sufficient space available. 

Note that `readMessageType(ByteBuffer)` method (which is called upon receiving a new message) does not actually check the contents of the buffer. Extension protocol has already taken care of that, and we can safely assume that the received message is really `YourIP`.

As described in [IoC and Customization](../custom), the easiest way to add a custom message is to use a contribution method. In order to use a contribution method (or adding something into the IoC container in general) we'll need to create a custom Guice module.

For now, the only contribution we need to do is register a custom message handler for a literal message ID. Peers will exchange information on what types of messages they support during the extended handshake.

```java
package yourip;

import bt.module.ProtocolModule;
import bt.module.ServiceModule;
import com.google.inject.Binder;
import com.google.inject.Module;

public class YourIPModule implements Module {

    @Override
    public void configure(Binder binder) {
        ProtocolModule.contributeExtendedMessageHandler(binder)
            .addBinding(YourIP.id()).to(YourIPMessageHandler.class);
    }
}
```

Now it's time to make use of the new message and create a messaging agent that will consume and produce messages of this particular type.

```java
package yourip;

import bt.net.Peer;
import bt.peer.IPeerRegistry;
import bt.protocol.Message;
import bt.protocol.extended.ExtendedHandshake;
import bt.torrent.annotation.Consumes;
import bt.torrent.annotation.Produces;
import bt.torrent.messaging.MessageContext;
import com.google.inject.Inject;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class YourIPMessenger {

    private final IPeerRegistry peerRegistry;

    private Set<Peer> supportingPeers;
    private Set<Peer> known;

    @Inject
    public YourIPMessenger(IPeerRegistry peerRegistry) {
        this.peerRegistry = peerRegistry;
        this.supportingPeers = new HashSet<>();
        this.known = new HashSet<>();
    }

    @Consumes
    public void consume(ExtendedHandshake handshake, MessageContext context) {
        Peer peer = context.getPeer();
        if (handshake.getSupportedMessageTypes().contains(YourIP.id())) {
            supportingPeers.add(peer);
        } else if (supportingPeers.contains(peer)) {
            supportingPeers.remove(peer);
        }
    }

    @Consumes
    public void consume(YourIP message, MessageContext context) {
        System.out.println("I am " + peerRegistry.getLocalPeer() +
                ", for peer " + context.getPeer() + " my external address is " + message.getAddress());
    }

    @Produces
    public void produce(Consumer<Message> messageConsumer, MessageContext context) {
        Peer peer = context.getPeer();
        if (supportingPeers.contains(peer) && !known.contains(peer)) {
            String address = context.getPeer().getInetSocketAddress().toString();
            messageConsumer.accept(new YourIP(address));
            known.add(peer);
        }
    }
}
```

The interesting thing here is that messaging agents do not need to have any specific Java type. Any object can act as a message consumer/producer. The rules are as follows:
 
* To act as a message consumer (i.e. to receive messages) the object may declare any number of methods, annotated with `@Consumes`, that have the following signature: `<T extends Message> (T message, MessageContext context):V`.
`T` can be any message type (using generic `Message` type is also allowed and lets you create "generic" consumers that receive all kinds of messages).

* To act as a message producer (i.e. to send messages) the object may declare any number of methods, annotated with `@Produces`, that have the following signature:  `(Consumer<Message> messageConsumer, MessageContext context):V`.

There is no restriction on the number of methods - only names have to be different - so a single object may be responsible for consuming and producing messages of several different types.

Messaging agents are also contributed via a dedicated method in `ProtocolModule`, so let's update our module's code:

```java
package yourip;

import bt.module.ProtocolModule;
import bt.module.ServiceModule;
import com.google.inject.Binder;
import com.google.inject.Module;

public class YourIPModule implements Module {

    @Override
    public void configure(Binder binder) {
        ProtocolModule.contributeExtendedMessageHandler(binder)
            .addBinding(YourIP.id()).to(YourIPMessageHandler.class);
            
        ServiceModule.contributeMessagingAgent(binder)
            .addBinding().to(YourIPMessenger.class);
    }
}
```

And that's all. The only thing left is to initialize a couple of Bt clients with the newly created module:
 
```java
package yourip;

import bt.Bt;
import bt.net.InetPeer;
import bt.net.Peer;
import bt.runtime.BtClient;
import bt.runtime.Config;
import yourip.mock.MockModule;
import yourip.mock.MockStorage;
import yourip.mock.MockTorrent;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class Main {

    private static final int[] ports = new int[] {6891, 6892};
    private static final Set<Peer> peers = new HashSet<Peer>() { {
        for (int port : ports) {
            add(new InetPeer(InetAddress.getLoopbackAddress(), port));
        }
    }};

    public static Set<Peer> peers() {
        return Collections.unmodifiableSet(peers);
    }

    public static void main(String[] args) throws InterruptedException {
        Collection<BtClient> clients = new HashSet<>();
        for (int port : ports) {
            clients.add(buildClient(port));
        }

        clients.forEach(BtClient::startAsync);

        Thread.sleep(10000);

        clients.forEach(BtClient::stop);
    }

    private static BtClient buildClient(int port) {
        Config config = new Config() {
            @Override
            public InetAddress getAcceptorAddress() {
                return InetAddress.getLoopbackAddress();
            }

            @Override
            public int getAcceptorPort() {
                return port;
            }

            @Override
            public Duration getPeerDiscoveryInterval() {
                return Duration.ofSeconds(1);
            }

            @Override
            public Duration getTrackerQueryInterval() {
                return Duration.ofSeconds(1);
            }
        };

        return Bt.client()
                .config(config)
                .module(YourIPModule.class)
                .module(MockModule.class)
                .storage(new MockStorage())
                .torrent(() -> new MockTorrent())
                .build();
    }
}
```

Here we create two clients that will listen on ports 6891 and 6892. You may also notice that there is another module that is being contributed: `MockModule`. It's a simple collection of stubs that will allow us to run this example without an actual torrenting session.

When run, the program will launch two Bt clients and wait for a little bit, while the clients exchange `YourIP` messages. In standard output you should see something like this:

```
[bt.net.pool.incoming-acceptor] INFO bt.net.PeerConnectionPool 
    - Opening server channel for incoming connections @ /192.168.1.2:6892
    
[bt.net.pool.incoming-acceptor] INFO bt.net.PeerConnectionPool 
    - Opening server channel for incoming connections @ /192.168.1.2:6891
    
I am /192.168.1.2:6891, for peer 0.0.0.0/0.0.0.0:6892 
    my external address is /192.168.1.2:49666
    
I am /192.168.1.2:6892, for peer /192.168.1.2:49666 
    my external address is 0.0.0.0/0.0.0.0:6892
    
I am /192.168.1.2:6892, for peer 0.0.0.0/0.0.0.0:6891 
    my external address is /192.168.1.2:49665
    
I am /192.168.1.2:6891, for peer /192.168.1.2:49665 
    my external address is 0.0.0.0/0.0.0.0:6891
    
[bt.net.pool.incoming-acceptor] ERROR bt.net.PeerConnectionPool 
    - Unexpected I/O error when listening to the incoming channel 
      @ /192.168.1.2:6892: java.nio.channels.AsynchronousCloseException
      
[bt.net.pool.incoming-acceptor] ERROR bt.net.PeerConnectionPool 
    - Unexpected I/O error when listening to the incoming channel 
    @ /192.168.1.2:6891: java.nio.channels.AsynchronousCloseException

Process finished with exit code 0
```

# **Conclusion**

In this brief tutorial we demonstrated how to use IoC modules and contributions to extend and customize Bt library. 

It's important to note that it's not something that is intended to be used by library users only. It's the core design principle that underlies all Bt code. 

In fact such essential parts of Bt as HTTP tracker integration, Peer Exchange, DHT and Extension Protocol itself are not hard-wired into the core, but rather contributed via the very same Guice modules and contribution methods. Such modularity is what makes Bt really shine.