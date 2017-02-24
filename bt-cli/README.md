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
* -d, --dir <File>     Target download location                  
* -f, --file <File>    Torrent metainfo file                     
-s, --seed             Continue to seed when download is complete
```

### Run

```
$ java -jar target/bt-launcher.jar --file /path/to/torrent --dir /save/to/here --seed
```

### Key bindings

`Press 'p' to pause/resume` _(GUI will "freeze" when paused)_

`Ctrl-C to quit`

### LGPL dependencies

Please note that this module includes LGPL dependencies and can't be released to Maven Central in binary form.
To use this module, you will need to build it from sources manually.
