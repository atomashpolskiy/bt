# Bt Examples

This module contains examples of **Bt** library usage. It uses a [Bootique](http://bootique.io) wrapper to seamlessly run the example code without requiring you to setup a run configuration (i.e. specify the main class and provide the classpath).

## See them in action

[Simple command-line torrent downloader](https://github.com/atomashpolskiy/bt/tree/master/examples/src/main/java/bt/example/cli)

## Usage

### Assemble the wrapper

Run ```mvn clean package```.

### List all available examples

```
$ java -jar target/examples.jar

Option                    Description                                        
------                    -----------                                        
--cliwrapper              Bt Example: Simple CLI wrapper                     
--config <yaml_location>  Specifies YAML config location, which can be a file
                            path or a URL.                                   
--help                    Prints this message.
```

(_config_ and _help_ are standard **Bootique** commands, and can be ignored in our case)

### Print the flags supported by some specific example

```
$ java -jar target/examples.jar --cliwrapper

Option (* = required)  Description                               
---------------------  -----------                               
-?, -h, --help                                                   
* -d, --dir <File>     Target download location                  
* -f, --file <File>    Torrent metainfo file                     
-s, --seed             Continue to seed when download is complete
```

### Finally, run the example with the required flags 

(note how ```--``` delimits the arguments that should be passed to the example code)

```
$ java -jar target/examples.jar --cliwrapper -- --file /path/to/torrent --dir /save/to/here --seed
```
