# Simple command-line torrent downloader

This module contains a simple CLI launcher for **Bt**.

**NOTE**: Currently, the CLI launcher can only work in headless mode under _Windows XP_. This mode is activated, if `-H` command line argument is present. GUI will be disabled, but you'll still be able to find out what's going on by inspecting the logs in current working directory.

## Usage

### Available options

```
$ java -jar target/bt-launcher.jar

Option (* = required)  Description                                     
---------------------  -----------                                     
-?, -h, --help                                                         
-H, --headless         Disable UI                                      
-S, --sequential       Download sequentially   
-a, --all              Download all files (file selection will be disabled)
* -d, --dir <File>     Target download location                        
-e, --encrypted        Enforce encryption for all connections          
-f, --file <File>      Torrent metainfo file                           
-i, --iface            Use specific network interface                  
-m, --magnet           Magnet URI                                      
-p, --port <Integer>   Listen on specific port for incoming connections
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

Add `-i <hostname>` or `-i <inetaddr>` to bind all connections to the specified address.

Add `-p <port>` to listen for incoming connections on the specified port.

Add `-e` flag to encrypt all peer connections.

Add `-S` flag to download the data in sequential order (e.g. for streaming content).

Add `-a` flag to download all files (i.e. to disable interactive file selection).

Add `-s` flag to continue seeding after torrent has been downloaded.

Add `-H` flag to run in headless mode, disabling the GUI.

Add `-v` flag to enable more verbose logging (especially useful in headless mode).

Add `--trace` flag to enable trace logging (loads of debugging information, useful to diagnose problems).

### Key bindings

`Press 'p' to pause/resume` _(GUI will "freeze" when paused)_

`Ctrl-C to quit`

### Shell wrapper

After you've run `mvn clean install -DskipTests`, all the main jars should be installed in your local Maven repository. Use the following shell wrapper to simplify launching the CLI client:

```bash
#!/bin/bash

java -jar /Users/atomashpolskiy/.m2/repository/com/github/atomashpolskiy/bt-cli/1.4-SNAPSHOT/bt-cli-1.4-SNAPSHOT.jar -m "magnet:?xt=urn:btih:$1" -d ~/Downloads
```

Save to this to a file, make it executable (`chmod +x <filename>` on Unix/Linux) and add the containing folder to `$PATH`. Then run:

```bash
$ myscript.sh AF0D9AA01A9AE123A73802CFA58CCAF355EB19F8
```

### LGPL dependencies

_Please note that this module includes bundled LGPL dependencies and can't be distributed or released to Maven Central in binary form under APL 2.0 license.
To use this module, you will need to build it from sources manually._

This product depends on 'Lanterna', a Java library for creating text-based terminal GUIs, which can be obtained at:
  * LICENSE:
    * https://github.com/mabe02/lanterna/blob/master/License.txt (GNU Lesser General Public License v3.0 only)
  * HOMEPAGE:
    * http://code.google.com/p/lanterna/
    * https://github.com/mabe02/lanterna
