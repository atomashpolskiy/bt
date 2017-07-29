/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad.messages;

import static the8472.bencode.Utils.prettyPrint;
import static the8472.utils.Functional.castOrThrow;
import static the8472.utils.Functional.tap;
import static the8472.utils.Functional.tapThrow;
import static the8472.utils.Functional.typedGet;

import the8472.bencode.PathMatcher;
import the8472.bencode.Tokenizer;
import the8472.utils.Functional;

import lbms.plugins.mldht.kad.BloomFilterBEP33;
import lbms.plugins.mldht.kad.DBItem;
import lbms.plugins.mldht.kad.DHT;
import lbms.plugins.mldht.kad.DHT.DHTtype;
import lbms.plugins.mldht.kad.DHT.LogLevel;
import lbms.plugins.mldht.kad.Key;
import lbms.plugins.mldht.kad.NodeList;
import lbms.plugins.mldht.kad.NodeList.AddressType;
import lbms.plugins.mldht.kad.PeerAddressDBItem;
import lbms.plugins.mldht.kad.messages.ErrorMessage.ErrorCode;
import lbms.plugins.mldht.kad.messages.MessageBase.Method;
import lbms.plugins.mldht.kad.messages.MessageBase.Type;
import lbms.plugins.mldht.kad.utils.AddressUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * @author Damokles
 * 
 */
public class MessageDecoder {
	
	public MessageDecoder(Function<byte[], Optional<Method>> transactionIdMapper, DHTtype type) {
		this.transactionIdMapper = transactionIdMapper;
		this.type = type;
	}
	
	Map<String, Object> rootMap;
	ByteBuffer raw;
	final Function<byte[], Optional<Method>> transactionIdMapper;
	final DHTtype type;
	
	public void toDecode(ByteBuffer rawMessage, Map<String, Object> map) {
		this.raw = rawMessage;
		this.rootMap = map;
	}

	public MessageBase parseMessage() throws MessageException, IOException {

		try {
			String msgType = getStringFromBytes((byte[]) rootMap.get(Type.TYPE_KEY), true);
			if (msgType == null) {
				throw new MessageException("message type (y) missing", ErrorCode.ProtocolError);
			}
			
			
			Optional<byte[]> version = typedGet(rootMap, MessageBase.VERSION_KEY, byte[].class);

			MessageBase mb = null;
			if (msgType.equals(Type.REQ_MSG.getRPCTypeName())) {
				mb = parseRequest(rootMap, transactionIdMapper, type);
			} else if (msgType.equals(Type.RSP_MSG.getRPCTypeName())) {
				mb = parseResponse(rootMap, transactionIdMapper);
			} else if (msgType.equals(Type.ERR_MSG.getRPCTypeName())) {
				mb = parseError(rootMap, transactionIdMapper);
			} else
				throw new MessageException("unknown RPC type (y="+msgType+")");

			if (mb != null) {
				version.ifPresent(mb::setVersion);
			}

			return mb;
		} catch (Exception e) {
			if(e instanceof MessageException)
				throw (MessageException)e;
			throw new IOException("could not parse message",e);
		}
	}

	/**
	 * @param map
	 * @return
	 */
	private MessageBase parseError (Map<String, Object> map, Function<byte[], Optional<Method>> transactionIdMapper) {
		Object error = map.get(Type.ERR_MSG.innerKey());
		
		int errorCode = 0;
		String errorMsg = null;
		
		if(error instanceof byte[])
			errorMsg = getStringFromBytes((byte[])error);
		else if (error instanceof List<?>)
		{
			List<Object> errmap = (List<Object>)error;
			try
			{
				errorCode = ((Long) errmap.get(0)).intValue();
				errorMsg = getStringFromBytes((byte[]) errmap.get(1));
			} catch (Exception e)
			{
				// do nothing
			}
		}
		
		Object rawMtid = map.get(MessageBase.TRANSACTION_KEY);
		
		if (errorMsg == null && (rawMtid == null || !(rawMtid instanceof byte[])))
			return null;

		byte[] mtid = (byte[]) rawMtid;
		
		ErrorMessage msg = new ErrorMessage(mtid, errorCode,errorMsg);
		
		typedGet(map, "id", byte[].class).filter(b -> b.length == Key.SHA1_HASH_LENGTH).ifPresent(h -> msg.setID(new Key(h)));
		
		transactionIdMapper.apply(mtid).ifPresent(m -> msg.method = m);

		return msg;
	}

	/**
	 * @param map
	 * @param srv
	 * @return
	 */
	private MessageBase parseResponse (Map<String, Object> map,  Function<byte[], Optional<Method>> transactionIdMapper) throws MessageException {

		byte[] mtid = (byte[]) map.get(MessageBase.TRANSACTION_KEY);
		if (mtid == null || mtid.length < 1)
			throw new MessageException("missing transaction ID",ErrorCode.ProtocolError);
		
		// responses don't have explicit methods, need to match them to a request to figure that one out
		Method m = transactionIdMapper.apply(mtid).orElse(Method.UNKNOWN);

		return parseResponse(map, m, mtid);
	}

	/**
	 * @param map
	 * @param msgMethod
	 * @param mtid
	 * @return
	 */
	private MessageBase parseResponse (Map<String, Object> map,	Method msgMethod, byte[] mtid) throws MessageException {
		Map<String, Object> args = (Map<String, Object>) map.get(Type.RSP_MSG.innerKey());
		if (args == null) {
			throw new MessageException("response did not contain a body",ErrorCode.ProtocolError);
		}

		byte[] hash = Optional.ofNullable(args.get("id"))
				.map(castOrThrow(byte[].class, (o) -> new MessageException("expected parameter 'id' to be a byte-string, got "+o.getClass().getSimpleName(), ErrorCode.ProtocolError)))
				.orElseThrow(() -> new MessageException("mandatory parameter 'id' missing", ErrorCode.ProtocolError));
		byte[] ip = (byte[]) map.get(MessageBase.EXTERNAL_IP_KEY);

		if (hash.length != Key.SHA1_HASH_LENGTH) {
			throw new MessageException("invalid or missing origin ID",ErrorCode.ProtocolError);
		}

		Key id = new Key(hash);
		
		MessageBase msg = null;

		switch (msgMethod) {
		case PING:
			msg = new PingResponse(mtid);
			break;
		case PUT:
			msg  = new PutResponse(mtid);
			break;
		case ANNOUNCE_PEER:
			msg = new AnnounceResponse(mtid);
			break;
		case FIND_NODE:
			if (!args.containsKey("nodes") && !args.containsKey("nodes6"))
				throw new MessageException("received response to find_node request with neither 'nodes' nor 'nodes6' entry", ErrorCode.ProtocolError);
				//return null;
			
			msg = tapThrow(new FindNodeResponse(mtid), (m) -> {
				extractNodes(args, "nodes", DHTtype.IPV4_DHT).ifPresent(n -> m.setNodes(n));
				extractNodes(args, "nodes6", DHTtype.IPV6_DHT).ifPresent(n -> m.setNodes(n));
			});
			break;
		case SAMPLE_INFOHASHES:
			if(!args.containsKey("nodes") && !args.containsKey("nodes6") && !args.containsKey("samples"))
				throw new MessageException("Expected at least one of the following keys to be present: nodes, nodes6, samples", ErrorCode.ProtocolError);
			
			byte[] samples = typedGet(args, "samples", byte[].class).orElse(null);
			
			if(samples != null && samples.length % 20 != 0)
				throw new MessageException("samples length must be a multiple of 20", ErrorCode.ProtocolError);
			
			SampleResponse smp = new SampleResponse(mtid);
			
			if(samples != null)
				smp.samples = ByteBuffer.wrap(samples);
			
			typedGet(args, "num", Long.class).ifPresent(l -> smp.setNum(l.intValue()));
			typedGet(args, "interval", Long.class).ifPresent(l -> smp.setInterval(l.intValue()));

			extractNodes(args, "nodes", DHTtype.IPV4_DHT).ifPresent(smp::setNodes);
			extractNodes(args, "nodes6", DHTtype.IPV6_DHT).ifPresent(smp::setNodes);
			
			msg = smp;
			
			break;
		case GET:
			
			GetResponse get = new GetResponse(mtid);
			
			extractNodes(args, "nodes", DHTtype.IPV4_DHT).ifPresent(get::setNodes);
			extractNodes(args, "nodes6", DHTtype.IPV6_DHT).ifPresent(get::setNodes);
			
			PathMatcher m = new PathMatcher(Type.RSP_MSG.innerKey(),"v");
			Tokenizer t = new Tokenizer();
			m.tokenizer(t);
			ByteBuffer rawVal = m.match(raw);
			
			get.setRawValue(rawVal);
			
			typedGet(args, "token", byte[].class).ifPresent(get::setToken);;
			typedGet(args, "k", byte[].class).ifPresent(get::setKey);
			typedGet(args, "sig", byte[].class).ifPresent(get::setSignature);
			typedGet(args, "seq", Long.class).ifPresent(get::setSequenceNumber);
			
			
			msg = get;
			
			break;
		case GET_PEERS:
			byte[] token = Functional.typedGet(args, "token", byte[].class).orElse(null);
			Optional<NodeList> nodes = extractNodes(args, "nodes", DHTtype.IPV4_DHT);
			Optional<NodeList> nodes6 = extractNodes(args, "nodes6", DHTtype.IPV6_DHT);

			
			List<DBItem> dbl = null;
			
			@SuppressWarnings("unchecked")
			List<byte[]> vals = Optional.ofNullable(args.get("values"))
				.map(castOrThrow(List.class, val -> new MessageException("expected 'values' field in get_peers to be list of strings, got "+val.getClass(), ErrorCode.ProtocolError)))
				.orElse(Collections.EMPTY_LIST);

			if(vals.size() > 0)
			{
				dbl = new ArrayList<>(vals.size());
				for (int i = 0; i < vals.size(); i++)
				{
					// only accept ipv4 or ipv6 for now
					if (vals.get(i).length != DHTtype.IPV4_DHT.ADDRESS_ENTRY_LENGTH && vals.get(i).length != DHTtype.IPV6_DHT.ADDRESS_ENTRY_LENGTH)
						continue;
					dbl.add(new PeerAddressDBItem(vals.get(i), false));
				}
			}
			
			byte[] peerFilter = (byte[]) args.get("BFpe");
			byte[] seedFilter = (byte[]) args.get("BFse");
			
			if((peerFilter != null && peerFilter.length != BloomFilterBEP33.m/8) || (seedFilter != null && seedFilter.length != BloomFilterBEP33.m/8))
				throw new MessageException("invalid BEP33 filter length", ErrorCode.ProtocolError);
			
			if (dbl != null || nodes.isPresent() || nodes6.isPresent())
			{
				GetPeersResponse resp = new GetPeersResponse(mtid);
				nodes.ifPresent(l -> resp.setNodes(l));
				nodes6.ifPresent(l -> resp.setNodes(l));
				resp.setPeerItems(dbl);
				resp.setToken(token);
				resp.setScrapePeers(peerFilter);
				resp.setScrapeSeeds(seedFilter);
				msg = resp;
				break;
			}
			
			throw new MessageException("Neither nodes nor values in get_peers response",ErrorCode.ProtocolError);
		case UNKNOWN:
			msg = new UnknownTypeResponse(mtid);
			break;
 		default:
			throw new RuntimeException("should not happen!!!");
		}
		
		if(ip != null) {
			InetSocketAddress addr = AddressUtils.unpackAddress(ip);
			msg.setPublicIP(addr);
			if(addr == null)
				DHT.logError("could not decode IP: " + prettyPrint(map));
		}
		
		msg.setID(id);
		
		return msg;
	}
	
	private Optional<NodeList> extractNodes(Map<String, Object> args, String key, DHTtype nodesType) throws MessageException {
		byte[] raw = typedGet(args, key, byte[].class).orElse(null);
		if(raw == null)
			return Optional.empty();
		if(raw.length % nodesType.NODES_ENTRY_LENGTH != 0)
			throw new MessageException("expected "+key+" length to be a multiple of "+nodesType.NODES_ENTRY_LENGTH+", received "+raw.length, ErrorCode.ProtocolError);
		return Optional.of(NodeList.fromBuffer(ByteBuffer.wrap(raw), nodesType == DHTtype.IPV4_DHT ? AddressType.V4 : AddressType.V6));
	}
	
	
	/**
	 * @param map
	 * @return
	 */
	private MessageBase parseRequest (Map<String, Object> map,  Function<byte[], Optional<Method>> transactionIdMapper, DHTtype type) throws MessageException {
		Object rawRequestMethod = map.get(Type.REQ_MSG.getRPCTypeName());
		Map<String, Object> args = typedGet(map, Type.REQ_MSG.innerKey(), Map.class).orElseThrow(() -> new MessageException("expected a bencoded dictionary under key " + Type.REQ_MSG.innerKey(), ErrorCode.ProtocolError));
		
		if (rawRequestMethod == null || args == null)
			return null;

		byte[] mtid = Functional.typedGet(map, MessageBase.TRANSACTION_KEY, byte[].class).filter(tid -> tid.length > 0).orElseThrow(() -> new MessageException("missing or zero-length transaction ID in request", ErrorCode.ProtocolError));
		byte[] hash = Functional.typedGet(args,"id", byte[].class).filter(id -> id.length == Key.SHA1_HASH_LENGTH).orElseThrow(() -> new MessageException("missing or invalid node ID", ErrorCode.ProtocolError));
		
		Key id = new Key(hash);

		MessageBase msg = null;

		String requestMethod = getStringFromBytes((byte[]) rawRequestMethod, true);
		
		
		Method method = Optional.ofNullable(MessageBase.messageMethod.get(requestMethod)).orElse(Method.UNKNOWN);
		
		switch(method) {
			case PING:
				msg = new PingRequest();
				break;
			case FIND_NODE:
			case GET_PEERS:
			case GET:
			case SAMPLE_INFOHASHES:
			case UNKNOWN:
				
				hash = Stream.of(args.get("target"), args.get("info_hash")).filter(byte[].class::isInstance).findFirst().map(byte[].class::cast).orElseThrow(() -> {
					if(method == Method.UNKNOWN)
						return new MessageException("Received unknown Message Type: " + requestMethod,ErrorCode.MethodUnknown);
					return new MessageException("missing/invalid target key in request",ErrorCode.ProtocolError);
				});
				
				if (hash.length != Key.SHA1_HASH_LENGTH) {
					throw new MessageException("invalid target key in request",ErrorCode.ProtocolError);
				}
					
				Key target = new Key(hash);
				
				AbstractLookupRequest req;
				
				switch(method) {
					case FIND_NODE:
						req = new FindNodeRequest(target);
						break;
					case GET_PEERS:
						req = new GetPeersRequest(target);
						break;
					case GET:
						req = new GetRequest(target);
						break;
					case SAMPLE_INFOHASHES:
						req = new SampleRequest(target);
						break;
					default:
						req = new UnknownTypeRequest(target);
				}
				
				@SuppressWarnings("unchecked")
				List<byte[]> explicitWants = Optional.ofNullable(args.get("want")).map(castOrThrow(List.class, w -> new MessageException("invalid 'want' parameter, expected a list of byte-strings"))).orElse(null);
						
				if(explicitWants != null)
					req.decodeWant(explicitWants);
				else {
					req.setWant4(type == DHTtype.IPV4_DHT);
					req.setWant6(type == DHTtype.IPV6_DHT);
				}
				
				
				if (req instanceof GetPeersRequest)
				{
					GetPeersRequest peerReq = (GetPeersRequest) req;
					peerReq.setNoSeeds(Long.valueOf(1).equals(args.get("noseed")));
					peerReq.setScrape(Long.valueOf(1).equals(args.get("scrape")));
				}
				
				if(req instanceof GetRequest) {
					GetRequest getReq = (GetRequest) req;
					typedGet(args, "seq", Long.class).ifPresent(seq -> {
						getReq.setSeq(seq);
					});
				}
				
				msg = req;
				
				break;
			case PUT:
				
				PathMatcher m = new PathMatcher(Type.REQ_MSG.innerKey(),"v");
				Tokenizer t = new Tokenizer();
				m.tokenizer(t);
				ByteBuffer rawVal = m.match(raw);
				
				msg = tapThrow(new PutRequest(), put -> {
					if(rawVal != null)
						put.setValue(rawVal);
					put.pubkey = Functional.typedGet(args, "k", byte[].class).orElse(null);
					put.sequenceNumber = Functional.typedGet(args, "seq", Long.class).orElse(-1L);
					put.expectedSequenceNumber = Functional.typedGet(args, "cas", Long.class).orElse(-1L);
					put.salt = Functional.typedGet(args, "salt", byte[].class).filter(b -> b.length > 0).orElse(null);
					put.signature = Functional.typedGet(args, "sig", byte[].class).orElse(null);
					put.token = Functional.typedGet(args, "token", byte[].class).filter(b -> b.length > 0).orElseThrow(() -> new MessageException("missing or invalid token in PUT request"));
					put.validate();
				});
				break;
			case ANNOUNCE_PEER:
				
				hash = Functional.typedGet(args, "info_hash", byte[].class).filter(b -> b.length == Key.SHA1_HASH_LENGTH).orElse(null);
				int port = Functional.typedGet(args, "port", Long.class).filter(p -> p > 0 && p <= 65535).orElse(0L).intValue();
				byte[] token = Functional.typedGet(args, "token", byte[].class).orElse(null);
				boolean isSeed = Long.valueOf(1).equals(args.get("seed"));
				
				if(hash == null || token == null || port == 0)
					throw new MessageException("missing or invalid mandatory arguments (info_hash, port, token) for announce", ErrorCode.ProtocolError);
				if(token.length == 0)
					throw new MessageException("zero-length token in announce_peer request. see BEP33 for reasons why tokens might not have been issued by get_peers response", ErrorCode.ProtocolError);

				Key infoHash = new Key(hash);

				msg = tap(new AnnounceRequest(infoHash, port, token), ar -> {
					ar.setSeed(isSeed);
					typedGet(args, "name", byte[].class).ifPresent(b -> ar.setName(ByteBuffer.wrap(b)));
				});

				break;
		}
		
		
		if (msg != null) {
			msg.setMTID(mtid);
			msg.setID(id);
		}

		return msg;
	}
	
	private static String getStringFromBytes (byte[] bytes, boolean preserveBytes) {
		if (bytes == null) {
			return null;
		}
		try {
			return new String(bytes, preserveBytes ? StandardCharsets.ISO_8859_1 : StandardCharsets.UTF_8);
		} catch (Exception e) {
			DHT.log(e, LogLevel.Verbose);
			return null;
		}
	}

	private static String getStringFromBytes (byte[] bytes) {
		return getStringFromBytes(bytes, false);
	}
}
