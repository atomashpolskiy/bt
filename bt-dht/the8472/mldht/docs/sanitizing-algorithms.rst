Assumptions
-----------

- NATs are a sad reality. Avoid penalizing multiple nodes on one IP too harshly. But also avoid additional ports giving an attacker any benefit.
- unsolicited datagrams may come from spoofed IP addresses. thus any penalty mechanism - except spam filtering - should not be easily triggered by unsolicited messages. otherwise an attacker could use it to flush the routing table, penalize other nodes etc.
- routing table != outgoing lookups != servicing read requests != servicing write requests. fine-grained penalties are possible
- BEP42 does not apply. as written it is limited to get-peers lookup terminal sets, nothing else. At best we can use compliance with it to skip a few checks we still will have to do for non-compliant nodes

Nomenclature
------------

RPC
  remote procedure call. In the outgoing direction. A pair of request-response messages and associated internal state.
Lookup
  a sequence of RPCs, often visiting contacts not contained in the routing table
RTE 
  routing table entry. consists of ip, port, ID and some counters and points in time
IP
  IP address, any port
socket address
  IP address, port tuple
  

Expected ID for RPCs
--------------------

Almost all outgoing RPCs should have an associated *expected ID*. For routing table maintenance this is the current ID of the routing table entry. For lookups it is the ID that was obtained from the ``nodes`` list. Only few pings such as those to bootstrap nodes or from BT PORT messages don't come with an associated ID.

The trust placed in that expectation varies depending on the source. But violations are a good sign something is wrong.



Routing table sanitizing
------------------------

* Only one RTE per IP
* Main buckets MUST NOT contain RTEs that have not been verified as reachable by a RPC. I.e. do not populate them from unsolicited requests
* for the purpose of routing table insertion and maintenance *verified* means that all of the following must match precisely: 

  - transaction ID
  - if RTE does not yet exist
  
    - reply source socket address must match request destination socket address 
    - reply ID must match expected ID from whatever triggered the RPC

  - if RTE exists
 
    - reply source socket address must match RTE
    - reply ID must match RTE

* If the transaction ID and socket address (not just IP) matches but the expected ID does not, then the matching RTE must be evicted immediately. Additionally all RTEs it shared a bucket with must be actively re-verified, modulo a small backoff period. This allows swift cleanup of the routing table when malicious nodes are detected.
* for routing table maintenance a socket address mismatch should be considered as a RPC timeout. It may not lead to immediate eviction but if the node keeps behaving inconsistently it will eventually time out. This restriction does not apply to lookups, responses from the wrong port can still contain useful values or nodes. Nodes that have funky port mapping behavior (e.g. behind mobile CGN) are not useful for the routing table.
* Verification/Ping maintenance must be delayed for at least 90 seconds after the last unsolicited message has been received from that RTE. This allows NAT table mappings to time out. This way we can get rid of all NATed nodes that don't use endpoint indepentent mappings/full cone NAT. This backoff is also important when promoting replacement entries.
* since lookups are seeded from the routing table and some maintenance lookups may also visit nearby nodes which might be in the routing table all RPCs of all types should be used to check existing routing table entries
* generally put a focus on the maintenance of local buckets. this is where bad entries result in other people's lookups getting "stuck" exhausting all the contacts presented instead of skipping over most of them like they do during the homing-in-on-target phase of a lookup.
* if the routing table is lazy about evicting nodes that failed to respond to RPCs from non-full buckets (as per kademlia paper) then it should apply stricter standards about what to include in ``nodes`` lists, otherwise it's returning probably-unreachable nodes to others, leading to wasted lookup traffic.
* multi-homed nodes should use the observer-independence verification approach from BEP 45. https://github.com/bittorrent/bittorrent.org/blob/master/html/beps/bep_0045.rst#shared-routing-table


Sanitizing Lookups
------------------

Lookups are fairly difficult to do precisely [closest set], efficiently [few RPCs] and swiftly [time until we are certain the closest set is stable] and reliably [avoid bogus contacts]. Remote nodes generally present very inconsistent and unreliable data: 50% non-reachable contacts, duplicate IDs over different socket addresses, different ports, multiple IDs for the same socket address and outright malicious contacts injected into their routing tables.

I have not yet settled on a final strategy that I could recommend, but I have found that combining some of the following approaches does significantly improve performance / weeds out bogus contacts:

* ignore responses that don't match the expected ID.
* score contacts based on how many other nodes returned them. this is a good tie-breaker when multiple ports are returned for the same contact
* use a cache. this shortens the path towards the closest set and thus the opportunities for attackers. implemented as multi-homed routing table with home IDs from target IDs of recent lookups. much shorter timeouts, more  aggressive replacement strategy
* retransmits can improve precision, but do so at the expense of swiftness. but this tradeoff is not a bad one to make, e.g. in announces vs. scrapes
* generally avoid sending multiple requests to the same IP even if suggested ports or IDs differ. but use scoring system to decide otherwise in some circumstances. related to retransmit logic
* ignore contacts that have been returned by a single node and N RPCs to other contacts supplied by it are already in flight or have failed. this reduces the impact of collusion or polluted routing tables (those two problems are nearly indistinguishable). For this it is necessary to keep a graph of which contacts have been suggested by which previously visited contacts.
* filter contacts based on the mismatch oracle (see below)
* filter contacts based on a recently observed non-reachability -> reduces packets wasted on nodes that inject themselves into many routing tables but stay silent on get_peers
* throttle outgoing requests on a per-ip basis, this serves the double purpose of limiting DoS amplification and avoiding repeatedly contacting nodes that insert themselves under many different IDs into other routing tables  
* [todo] de-prioritize teredo addresses on ipv6?
* [todo] factor BEP42 compliance into scoring system
* [todo] Evaluate the disjoint path approach of S/Kademlia http://doc.tm.uka.de/SKademlia_2007.pdf


ID Mismatch Oracle
------------------

Similar to the routing table observing all RPCs we can install an oracle that remembers the *ip,port,ID* tuples of replies to RPCs where an ID mismatch was detected. Such an initial mismatch merely makes a node a suspect, because we can't be certain that our ID expectation was reliable.

The oracle can then do several things:

sanitize lookups
 when a lookup receives a ``nodes`` list it can check against the oracle whether the socket address is known and the suggested ID from the contact matches the last seen ID.
 
 Observed IDs obviously have more weight than those reported by other parties. 
passive detection.
 just keep the observation of the mismatch around, without immediately acting on it. if it then sees another RPC from the same socket address with yet another ID, ban the IP
active detection
  send another RPC, this time expecting the new ID (the one contained in the reply). If a second mismatch is deteected, ban the IP. It is very unlikely that a node changes IDs just in the timeframe where we have detected the first mismatch, even if the source of the first expectation was untrusted. active detection obviously needs some pacing


This is how I found the polluted routing tables in LT nodes.


The sanitizing and passive features work best on very active nodes which are likely to visit malicious nodes multiple times. The active mechanism is more suited for slow nodes which don't generate much traffic and can afford sending another validating RPC every now and then.

Sanitizing Writes
-----------------

This one is easy. Derive token from origin IP, port, ID, target ID and a rotating secret.

If a remote node can't even keep its ID or port stable between two requests there is no point in storing its data.