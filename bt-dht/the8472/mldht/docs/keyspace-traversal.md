# Efficient Keyspace Traversal


The following outlines the algorithm used by mldht for BEP 51 indexing. Other approaches and various tweaks are possible, which is why the BEP does not prescribe any particular approach.


## Goals


* get reasonably uniform whole-keyspace coverage
* minimize the amount of state that has to be kept in memory
* avoid visiting nodes more than once

## Approach


1. create a temporary routing table with the home ID of 0x00..., the home ID will be referred to as *cursor*
  1. the routing table should initially consist of 1 bucket spanning the whole keyspace
  2. have each bucket in that routing table track the following: up to 8 responded nodes, an unlimited amount of candidate nodes and an unlimited amount of queried nodes
2. seed the initial bucket's candidate list from the node's normal routing table and possibly from a lookup targeted at *cursor*
3. query non-visited nodes within the current bucket's candidate list with a `target` ID set to a random ID within the bucket's range
4. split buckets and redistribute nodes when the bucket covering the *cursor* would overflow the responded nodes list
5. when the candidates list within the bucket is exhausted advance *cursor* to the lowest ID covered by the next in natural keyspace order, i.e. in the direction from 0x00... to 0xFF...
  1. perform bucket merges below the new cursor.
  2. populate the new home bucket with additional candidates from the normal routing 
6. repeat queries, splitting and cursor-advancement until the advancement would point beyond the keyspace


The approach achieves its goals as follows:
* bucket splitting + bucket-scoped `target` IDs exploits the general kademlia properties about the routing table locality of remote nodes, i.e. it is locally exhaustive for any particular bucket
* at any given point the non-home buckets will still inherit some candidates from splitting which can be queried after the cursor gets advanced
* since only nodes in the current bucket are visited and no visited nodes are queried again each node only gets visited once (assuming contacts supplied by remote nodes are correct)

Note: In practice clients will want to implement  the ID mismatch oracle, per-ip request limiting and non-reachability cache described in [sanitizing algorithms](sanitizing-algorithms.rst) to minimize the number of junk contacts they will attempt to visit during a lookup. Otherwise 80% of the requests could easily be wasted on non-responsive or ID-spoofing nodes present in many routing tables. 

## Limitations


The bucket merge loses information, which means complete tracking of the BEP51 ``interval`` is not possible, so this approach can only be used once every 6 hours. Considering that there are millions of nodes and a well-behaved indexer should limit the rate at which it sends requests that should not be an issue.
Algorithms that perform sparser sampling of the node population or that keep more state can still exploit that feature. 