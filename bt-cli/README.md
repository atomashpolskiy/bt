# Simple command-line torrent downloader

This module contains a simple CLI launcher for **Bt**.

## Usage

### Assemble the wrapper

```mvn clean package```

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
