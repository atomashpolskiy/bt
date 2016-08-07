[![Build Status](https://travis-ci.org/atomashpolskiy/bt.svg?branch=master)](https://travis-ci.org/atomashpolskiy/bt)

**Bt** aims to be the ultimate BitTorrent software created in Java. It offers good performance, reliability and is highly customizable. With Bt you can create a production-grade BitTorrent client in a matter of minutes. Bt is still in its' early days, but is actively developed and designed with stability and maintainability in mind.

## Quick Links

[Examples](https://github.com/atomashpolskiy/bt/tree/master/examples)

## A really ascetic code sample

```java
public static void main(String[] args) {

  // get metainfo file url and download location from the program arguments
  URL metainfoUrl = ...;
  File targetDirectory = ...;

  // create default shared runtime without extensions
  BtRuntime runtime = BtRuntimeBuilder.defaultRuntime();
  
  // create file system based backend for torrent contents
  DataAccessFactory daf = new FileSystemDataAccessFactory(targetDirectory);
  
  // create client
  BtClient client = Bt.client(runtime).url(metainfoUrl).build(daf);
  
  // launch
  client.startAsync(state -> {
      if (state.getPiecesRemaining() == 0) {
          client.stop();
      }
  }, 1000).join();
}
```
