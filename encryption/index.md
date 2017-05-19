---
layout: page
title: Message Encryption
permalink: /encryption/
---

# **Overview**

Bt provides full support for [Message Stream Encryption](http://wiki.vuze.com/w/Message_Stream_Encryption), an encapsulation protocol that serves to circumvent throttling and blocking of BitTorrent traffic by ISPs. It makes use of RC4-drop1024 stream cipher with 160-bit key to obfuscate all messages and data exchanged between two peers. You may learn more about the technical details of the protocol by visiting the link above, and this page is intended to give you all necessary information on how to use it in the context of Bt library.

# **Configuration**

The only thing that is required to setup MSE is to specify the preferred policy regarding encryption. It can be done when assembling a Bt client by overriding default configuration and passing it to the Bt runtime or client builder:
 
```java
Config config = new Config() {
    @Override
    public EncryptionPolicy getEncryptionPolicy() {
        return EncryptionPolicy.REQUIRE_ENCRYPTED;
    }
};
BtRuntimeBuilder builder = BtRuntime.builder(config);
// continue runtime setup
```

There are four values, ranging from raw BitTorrent messaging to paranoid encrypt-every-bit policy:

* REQUIRE_PLAINTEXT
* PREFER_PLAINTEXT
* PREFER_ENCRYPTED
* REQUIRE_ENCRYPTED

Default local policy is PREFER_PLAINTEXT, which effectively means: avoid using encryption if possible, but still accept incoming connections from peers that strictly require encryption.

Choosing one of these policies will affect different areas of BitTorrent protocol, most importantly the encryption negotiation procedure that is conducted when initiating or receiving a new connection.

# **Peer policies**

Depending on the way in which the local client discovers new peers, some assumptions might need to be made regarding peer's encryption policy. Unless indicated otherwise, Bt by default assumes that a peer supports both plaintext and encrypted messaging, but prefers to use plaintext (PREFER_PLAINTEXT policy). This applies to peers that have just been discovered via UDP tracker or DHT.

In contrast, HTTP trackers, Peer Exchange protocol and Extended Protocol provide the means to communicate information on the peer's encryption policy to the requestor. This means that peers discovered via HTTP tracker or PEX will indicate the actual level of encryption support.

# **Encryption negotiation**

All policies are considered compatible (meaning that Bt will attempt  to establish or receive a connection) except REQUIRE_PLAINTEXT and REQUIRE_ENCRYPTED.

If a peer's policy is REQUIRE_PLAINTEXT and local policy is compatible (not REQUIRE_ENCRYPTED), then the standard BitTorrent handshake will be performed for an outgoing connection to this peer.

If a peer's policy is PREFER_PLAINTEXT and local policy is either REQUIRE_PLAINTEXT or PREFER_PLAINTEXT, then the standard BitTorrent handshake will be performed for an outgoing connection to this peer.

For incoming connections an automatic protocol detection is performed. If the first 20 incoming bytes are the standard BitTorrent handshake prefix, and local policy is compatible with REQUIRE_PLAINTEXT (i.e. is not REQUIRE_ENCRYPTED), then MSE negotiation will not be performed, and a standard connection will be established.

In all other cases Bt will make an attempt to negotiate encryption, and the final protocol will or will not be selected based on the following rules:

* for outgoing connections Bt will offer support for either plaintext or encryption or both (depending on local policy); peer will either select the policy that suits her or terminate the connection
* outgoing connection to a peer that does not support MSE negotiation (unlikely, but still happens in the wild) will be terminated by the peer and currently<sup>*</sup> will not be reattempted via standard BitTorrent protocol
* for incoming connections policy will be selected from the peer's crypto_provide, according to the table below:

|Local   \    Peer  | Require plaintext    | Support both plaintext and encrypted | Require encrypted    |
|------------------:|----------------------|--------------------------------------|----------------------|
| REQUIRE_PLAINTEXT | Plaintext            | Plaintext                            | Terminate connection |
| PREFER_PLAINTEXT  | Plaintext            | Plaintext                            | Encrypted            |
| PREFER_ENCRYPTED  | Plaintext            | Encrypted                            | Encrypted            |
| REQUIRE_ENCRYPTED | Terminate connection | Encrypted                            | Encrypted            |

<sup>*</sup> If a peer refuses an incoming MSE negotiation request and drops the connection, then the BitTorrent client might try to reconnect to this peer using the standard protocol. Fallback to the standard protocol is not yet implemented in Bt, but might be added in the future.

### **Advertising support for encryption to peers**

Support for encryption can be advertised by the following means: HTTP tracker announce request, MSE `crypto_provide` bitfield, extended handshake. Bt uses them appropriately as indicated in the table below:

| Local policy      | HTTP tracker query | MSE crypto_provide | Extended handshake |
|-------------------|--------------------|--------------------|--------------------|
| REQUIRE_PLAINTEXT | -                  | 0x01               | e=0                |
| PREFER_PLAINTEXT  | supportcrypto=1    | 0x01 \| 0x02       | e=0                |
| PREFER_ENCRYPTED  | supportcrypto=1    | 0x01 \| 0x02       | e=1                |
| REQUIRE_ENCRYPTED | requirecrypto=1    | 0x02               | e=1                |