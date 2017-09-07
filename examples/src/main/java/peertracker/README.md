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
Uptime: PT15.004S

[f7a8e486ee974735e3295f896a6bc92d95c58f42]	total known peers:    149
	(                               /189.6.17.246:27417)	data available:    -	times discovered:      1,	times connected:      0,	times disconnected:      0
	(                             /185.193.184.13:16837)	data available:    -	times discovered:      1,	times connected:      0,	times disconnected:      0
	(                                   /46.116.78.47:1)	data available:    -	times discovered:      1,	times connected:      0,	times disconnected:      0
	(                             /130.105.197.39:64960)	data available:    -	times discovered:      1,	times connected:      0,	times disconnected:      0
	(                              /178.143.2.201:20303)	data available:    -	times discovered:      1,	times connected:      0,	times disconnected:      0
	(                               /189.82.9.155:54270)	data available: 100%	times discovered:      1,	times connected:      1,	times disconnected:      0
	(                             /24.209.224.237:38729)	data available:    -	times discovered:      1,	times connected:      0,	times disconnected:      0
	(                               /94.65.64.215:15925)	data available:    -	times discovered:      1,	times connected:      0,	times disconnected:      0
	(                             /103.198.97.104:17638)	data available:    -	times discovered:      1,	times connected:      0,	times disconnected:      0
	(                             /187.59.205.216:19667)	data available:    -	times discovered:      1,	times connected:      0,	times disconnected:      0
	(                              /78.141.77.236:24874)	data available:    -	times discovered:      1,	times connected:      0,	times disconnected:      0
	...
```