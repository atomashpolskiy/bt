# Peer tracker

Demonstrates how to hook into runtime to listen to peer discovery and connection events.

Runnable class `peertracker.Main` accepts one parameter: path to a file. Each line in this file should contain one valid magnet link, e.g.:

```
magnet:?xt=urn:btih:f1a8e486ee924735e3295f016a6bc92d95c58f23
magnet:?xt=urn:btih:1124aabbf141c0539bb449c0ce1c1ab0fb5072d9
magnet:?xt=urn:btih:47c1eb4b8ece4286e70ab7359d7c4c39b7fc9d32&tr=udp%3A%2F%2Ftracker.example.org%3A6969
```

Every 15 seconds the program will dump peer stats to a file `stats.txt` in current working directory. Example of file content:

```
Uptime: PT30.023S

[f1a8e486ee924735e3295f016a6bc92d95c58f23]	total known peers:    150
	(                               /80.216.7.246:10452)	times discovered:      5,	times connected:      0,	times disconnected:      0
	(                                    /93.44.93.22:1)	times discovered:      5,	times connected:      0,	times disconnected:      0
	(                              /77.131.74.145:41819)	times discovered:      5,	times connected:      0,	times disconnected:      0
	(                             /190.192.187.24:17308)	times discovered:      5,	times connected:      0,	times disconnected:      0
	(                              /190.153.80.67:52690)	times discovered:      5,	times connected:      0,	times disconnected:      0
	(                                  /212.16.20.246:1)	times discovered:      5,	times connected:      0,	times disconnected:      0
	(                              /87.69.209.225:34250)	times discovered:      5,	times connected:      0,	times disconnected:      0
	(                                  /190.80.95.211:1)	times discovered:      5,	times connected:      0,	times disconnected:      0
	(                                 /186.90.251.212:1)	times discovered:      5,	times connected:      0,	times disconnected:      0
	(                                  /77.250.172.29:1)	times discovered:      5,	times connected:      0,	times disconnected:      0
	(                                  /94.66.220.101:1)	times discovered:      5,	times connected:      0,	times disconnected:      0
	...
```