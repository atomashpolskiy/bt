# Simple command-line torrent downloader

This module contains a simple CLI launcher for **Bt**.

**NOTE #1**: Currently, all peer connections are established via [encryption negotation protocol](http://wiki.vuze.com/w/Message_Stream_Encryption) (also called MSE handshake). Therefore, in order to be able to connect to peers you must install [Java Cryptography Extension (JCE) Unlimited Strength Jurisdiction Policy](http://www.oracle.com/technetwork/java/javase/downloads/jce8-download-2133166.html). The reason for this requirement is that the [MSE RC4 cipher](http://wiki.vuze.com/w/Message_Stream_Encryption) uses 160 bit keys, while default Java installation allows at most 128 bit keys.

**NOTE #2**: Currently, the CLI launcher can only work in headless mode under _Windows_. This mode is activated, if `-H` command line argument is present. GUI will be disabled, but you'll still be able to find out what's going on by inspecting the logs in current working directory.

## Usage

### Available options

```
$ java -jar target/bt-launcher.jar

Option (* = required)  Description
---------------------  -----------
-?, -h, --help
-H, --headless         Disable UI
-S, --sequential       Download sequentially
* -d, --dir <File>     Target download location
-e, --encrypted        Enforce encryption for all connections
-f, --file <File>      Torrent metainfo file
-m, --magnet           Magnet URI
-s, --seed             Continue to seed when download is complete
--trace                Enable trace logging
-v, --verbose          Enable more verbose logging
```

### Run

#### Using .torrent file
```
$ java -jar target/bt-launcher.jar -f /path/to/torrent -d /save/to/here
```

#### Using magnet link
```
$ java -jar target/bt-launcher.jar -m "magnet:?xt=urn:btih:AF0D9AA01A9AE123A73802CFA58CCAF355EB19F0" -d /save/to/here
```

#### Options

Add `-e` flag to encrypt all peer connections.

Add `-S` flag to download the data in sequential order (e.g. for streaming content).

Add `-s` flag to continue seeding after torrent has been downloaded.

Add `-H` flag to run in headless mode, disabling the GUI.

Add `-v` flag to enable more verbose logging (especially useful in headless mode).

Add `--trace` flag to enable trace logging (loads of debugging information, useful to diagnose problems).

### Key bindings

`Press 'p' to pause/resume` _(GUI will "freeze" when paused)_

`Ctrl-C to quit`

### LGPL dependencies

_Please note that this module includes bundled LGPL dependencies and can't be distributed or released to Maven Central in binary form under APL 2.0 license.
To use this module, you will need to build it from sources manually._

This product depends on 'Lanterna', a Java library for creating text-based terminal GUIs, which can be obtained at:
  * LICENSE:
    * https://github.com/mabe02/lanterna/blob/master/License.txt (GNU Lesser General Public License v3.0 only)
  * HOMEPAGE:
    * http://code.google.com/p/lanterna/
    * https://github.com/mabe02/lanterna
