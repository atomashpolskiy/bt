# Simple command-line torrent downloader

This module contains a simple CLI launcher for **Bt**.

## Usage

### Assemble the wrapper

```mvn clean package -DskipTests=true``` from this module's directory or ```mvn clean package -DskipTests=true -Plgpl``` from project's root directory.

### Print help

```
$ java -jar target/bt-launcher.jar

Option (* = required)  Description                               
---------------------  -----------                               
-?, -h, --help                                                   
-S, --sequential       Download sequentially                     
* -d, --dir <File>     Target download location                  
* -f, --file <File>    Torrent metainfo file                     
-s, --seed             Continue to seed when download is complete
```

### Run

```
$ java -jar target/bt-launcher.jar -f /path/to/torrent -d /save/to/here
```

Add `-S` flag to download the data in sequential order (e.g. for streaming content).

### Key bindings

`Press 'p' to pause/resume` _(GUI will "freeze" when paused)_

`Ctrl-C to quit`

### LGPL dependencies

_Please note that this module includes bundled LGPL dependencies and can't be distributed or released to Maven Central in binary form.
To use this module, you will need to build it from sources manually._

This product depends on 'Lanterna', a Java library for creating text-based terminal GUIs, which can be obtained at:
  * LICENSE:
    * https://github.com/mabe02/lanterna/blob/master/License.txt (GNU Lesser General Public License v3.0 only)
  * HOMEPAGE:
    * http://code.google.com/p/lanterna/
    * https://github.com/mabe02/lanterna
