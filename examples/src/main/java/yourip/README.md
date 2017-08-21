# YourIP Messenger

_Full tutorial is available [HERE](http://atomashpolskiy.github.io/bt/extension-protocol/)._

Demonstrates how to use [BEP-10: Extended Protocol](http://www.bittorrent.org/beps/bep_0010.html) to create a simple custom message, that will let peers tell each other, what is their counterpart's external IP address (i.e. how peers see each other on the Internet). In real BitTorrent this information is provided in the extended handshake. 

When `yourip.Main` is run, the program will launch two Bt clients and wait for a little bit, while the clients exchange `YourIP` messages. In standard output you should see something like this:

```
[bt.net.pool.incoming-acceptor] INFO bt.net.PeerConnectionPool 
    - Opening server channel for incoming connections @ /127.0.0.1:6892
    
[bt.net.pool.incoming-acceptor] INFO bt.net.PeerConnectionPool 
    - Opening server channel for incoming connections @ /127.0.0.1:6891
    
I am localhost/127.0.0.1:6891, for peer localhost/127.0.0.1:6892 
    my external address is /127.0.0.1:49666
    
I am localhost/127.0.0.1:6892, for peer /127.0.0.1:49666 
    my external address is localhost/127.0.0.1:6892
    
I am localhost/127.0.0.1:6892, for peer localhost/127.0.0.1:6891 
    my external address is /127.0.0.1:49665
    
I am localhost/127.0.0.1:6891, for peer /127.0.0.1:49665 
    my external address is localhost/127.0.0.1:6891
    
[bt.net.pool.incoming-acceptor] ERROR bt.net.PeerConnectionPool 
    - Unexpected I/O error when listening to the incoming channel 
      @ /192.168.1.2:6892: java.nio.channels.AsynchronousCloseException
      
[bt.net.pool.incoming-acceptor] ERROR bt.net.PeerConnectionPool 
    - Unexpected I/O error when listening to the incoming channel 
    @ /192.168.1.2:6891: java.nio.channels.AsynchronousCloseException

Process finished with exit code 0
```
