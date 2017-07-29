/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.bt;

import the8472.bencode.BDecoder;
import the8472.bencode.BEncoder;
import the8472.bencode.Tokenizer.BDecodingException;
import the8472.bt.MetadataPool.Completion;
import the8472.utils.AnonAllocator;

import lbms.plugins.mldht.kad.DHT;
import lbms.plugins.mldht.kad.DHT.LogLevel;
import lbms.plugins.mldht.kad.Key;
import lbms.plugins.mldht.kad.utils.AddressUtils;
import lbms.plugins.mldht.kad.utils.ThreadLocalUtils;
import lbms.plugins.mldht.utils.NIOConnectionManager;
import lbms.plugins.mldht.utils.Selectable;

import static the8472.bencode.Utils.stripToAscii;
import static the8472.bt.PullMetaDataConnection.CONNECTION_STATE.*;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;

public class PullMetaDataConnection implements Selectable {
	
	public static interface MetaConnectionHandler {
		
		void onTerminate();
		default void onStateChange(CONNECTION_STATE oldState, CONNECTION_STATE newState) {};
		void onConnect();
	}
	
	public final static byte[] preamble = "\u0013BitTorrent protocol".getBytes(StandardCharsets.ISO_8859_1);

	
	public static final byte[] bitfield = new byte[8];
	static {
		// ltep
		bitfield[5] |= 0x10;
		// fast extension
		bitfield[7] |= 0x04;
		// BT port
		bitfield[7] |= 0x01;
	}
	
	public enum CONNECTION_STATE {
		STATE_INITIAL,
		STATE_CONNECTING,
		STATE_BASIC_HANDSHAKING,
		STATE_IH_RECEIVED,
		STATE_LTEP_HANDSHAKING,
		STATE_PEX_ONLY,
		STATE_GETTING_METADATA,
		STATE_CLOSED;
		
		public boolean neverConnected() {
			return this != STATE_INITIAL && this != STATE_CONNECTING;
		}
		
		
	}
	
	public enum CloseReason {
		NO_LTEP,
		NO_META_EXCHANGE,
		CONNECT_FAILED,
		OTHER
	}
	

	
	private static final int RCV_TIMEOUT = 25*1000;
	
	private static final int LTEP_HEADER_ID = 20;
	private static final int LTEP_HANDSHAKE_ID = 0;
	private static final int LTEP_LOCAL_META_ID = 7;
	private static final int LTEP_LOCAL_PEX_ID = 13;
	
	private static final int BT_BITFIELD_ID = 5;
	
	private static final int BT_HEADER_LENGTH = 4;
	private static final int BT_MSG_ID_OFFSET = 4; // 0-3 length, 4 id
	private static final int BT_LTEP_HEADER_OFFSET =  5; // 5 ltep id
	
	boolean						keepPexOnlyOpen;
	
	SocketChannel				channel;
	NIOConnectionManager		connManager;
	boolean						incoming;
	
	Deque<ByteBuffer>			outputBuffers			= new ArrayDeque<>();
	ByteBuffer					inputBuffer;

	boolean						remoteSupportsFastExtension;
	boolean						remoteSupportsPort;
	int							ltepRemoteMetadataExchangeMessageId;
	int							ltepRemotePexId = -1;
	byte[]						remotePeerId = new byte[20];
	public int					dhtPort = -1;
	public int					ourListeningPort = -1;
	
	
	MetadataPool				pool;
	Predicate<Key>				checker;
	byte[]						infoHash;

	int							outstandingRequests;
	int							maxRequests = 1;
	int							chunksReceived = 0;
	
	long						connectionOpenTime;
	long						connectTime;
	long						lastReceivedTime;
	long						lastUsefulMessage;
	long						connectTimeout = 5 * 1000;
	
	AtomicReference<CONNECTION_STATE> 			state = new AtomicReference<>(STATE_INITIAL) ;
	
	MetaConnectionHandler		metaHandler;
	
	InetSocketAddress			remoteAddress;
	String 						remoteClient;
	
	CloseReason					closeReason;
	
	public Consumer<List<InetSocketAddress>> pexConsumer = (x) -> {};
	public IntFunction<MetadataPool> 	poolGenerator = (i) -> new MetadataPool(i);
	
	public void keepPexOnlyOpen(boolean toggle) {
		keepPexOnlyOpen = toggle;
	}
	
	boolean setState(CONNECTION_STATE expected, CONNECTION_STATE newState) {
		return setState(EnumSet.of(expected), newState);
	}
	
	boolean setState(Set<CONNECTION_STATE> expected, CONNECTION_STATE newState) {
		CONNECTION_STATE current;
		do {
			current = state.get();
			if(!expected.contains(current))
				return false;
		} while(!state.weakCompareAndSet(current, newState));

		if(metaHandler != null)
			metaHandler.onStateChange(current, newState);
		return true;
	}
	
	public boolean isState(CONNECTION_STATE toTest) {return state.get() == toTest; }
	
	public boolean isIncoming() {return incoming;}
	
	public CONNECTION_STATE getState() {
		return state.get();
	}
	
	public Key getInfohash() {
		return new Key(infoHash);
	}
	
	public InetSocketAddress remoteAddress() {
		return remoteAddress;
	}
	
	public void setListener(MetaConnectionHandler handler) {
		metaHandler = handler;
	}
	
	public void setConnectTimeout(long t) {
		connectTimeout = t;
	}
	
	public long timeToConnect() {
		return connectTime - connectionOpenTime;
	}
	
	public int chunksReceived() {
		return chunksReceived;
	}
	
	// incoming
	public PullMetaDataConnection(SocketChannel chan)
	{
		channel = chan;
		incoming = true;
		connectionOpenTime = System.currentTimeMillis();

		try
		{
			channel.configureBlocking(false);
		} catch (IOException e)
		{
			DHT.log(e, LogLevel.Error);
		}
		
		remoteAddress = (InetSocketAddress) chan.socket().getRemoteSocketAddress();
		setState(STATE_INITIAL, STATE_BASIC_HANDSHAKING);
	}
	
	
	// outgoing
	public PullMetaDataConnection(byte[] infoHash, InetSocketAddress dest) throws IOException {
		this.infoHash = infoHash;
		this.remoteAddress = dest;

		channel = SocketChannel.open();
		//channel.socket().setReuseAddress(true);
		channel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
		channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
		channel.configureBlocking(false);
		//channel.bind(new InetSocketAddress(49002));
		
		setState(STATE_INITIAL, STATE_CONNECTING);
		sendBTHandshake();
	}
	
	private void sendBTHandshake() {
		ByteBuffer outputBuffer = ByteBuffer.allocate(20+8+20+20);
		byte[] peerID = new byte[20];
		ThreadLocalUtils.getThreadLocalRandom().nextBytes(peerID);

		outputBuffer.put(preamble);
		outputBuffer.put(bitfield);
		outputBuffer.put(infoHash);
		outputBuffer.put(peerID);


		outputBuffer.flip();
		outputBuffers.addLast(outputBuffer);
	}
	
	public SelectableChannel getChannel() {
		return channel;
	}
	
	public void registrationEvent(NIOConnectionManager manager, SelectionKey key) throws IOException {
		connManager = manager;
		lastReceivedTime = System.currentTimeMillis();

		if(isState(STATE_CONNECTING))
		{
			connectionOpenTime = System.currentTimeMillis();
			try {
				if(channel.connect(remoteAddress))
					connectEvent();
			} catch (IOException e) {
				terminate("connect failed " + e.getMessage(), CloseReason.CONNECT_FAILED);
			}
		} else
		{ // incoming
			metaHandler.onConnect();
		}
		
		connManager.interestOpsChanged(this);
		
		
		
		//System.out.println("attempting connect "+dest);
	}
	
	@Override
	public int calcInterestOps() {
		int ops = SelectionKey.OP_READ;
		if(isState(STATE_CONNECTING))
			ops |= SelectionKey.OP_CONNECT;
		if(!outputBuffers.isEmpty())
			ops |= SelectionKey.OP_WRITE;
					
		return ops;
	}
	
	@Override
	public void selectionEvent(SelectionKey key) throws IOException {
		if(key.isValid() && key.isConnectable())
			connectEvent();
		if(key.isValid() && key.isReadable())
			canReadEvent();
		if(key.isValid() && key.isWritable())
			canWriteEvent();
	}
	
	
	public void connectEvent() throws IOException {
		try {
			if(channel.isConnectionPending() && channel.finishConnect())
			{
				connectTime = System.currentTimeMillis();
				if(!setState(STATE_CONNECTING, STATE_BASIC_HANDSHAKING))
					return;
				connManager.interestOpsChanged(this);
				metaHandler.onConnect();
				//System.out.println("connection!");
			}
		} catch (IOException e) {
			//System.err.println("connect failed "+e.getMessage());
			terminate("connect failed", CloseReason.CONNECT_FAILED);
		}
	}
	
	
	public MetadataPool getMetaData() {
		return pool;
	}
	
	private void processInput() throws IOException {
		inputBuffer.flip();

		if(isState(STATE_BASIC_HANDSHAKING))
		{
			lastUsefulMessage = System.currentTimeMillis();
			
			boolean connectionMatches = true;
			byte[] temp = new byte[20];
			byte[] otherBitfield = new byte[8];

			// check preamble
			inputBuffer.get(temp);
			connectionMatches &= Arrays.equals(temp, preamble);


			// check LTEP support
			inputBuffer.get(otherBitfield);
			if((otherBitfield[5] & 0x10) == 0)
				terminate("peer does not support LTEP", CloseReason.NO_LTEP);
			
			remoteSupportsFastExtension = (otherBitfield[7] & 0x04) != 0;
			remoteSupportsPort	= (otherBitfield[7] & 0x01) != 0;

			// check infohash
			inputBuffer.get(temp);
			if(infoHash != null) {
				connectionMatches &= Arrays.equals(temp, infoHash);
			} else {
				infoHash = temp.clone();
				setState(STATE_BASIC_HANDSHAKING, STATE_IH_RECEIVED);
				// state callback may terminate
				if(isState(STATE_CLOSED))
					return;
			}


			// check peer ID
			inputBuffer.get(remotePeerId);
			if(remotePeerId[0] == '-' && remotePeerId[1] == 'S' && remotePeerId[2] == 'D' && remotePeerId[3] == '0' && remotePeerId[4] == '1' && remotePeerId[5] == '0' &&  remotePeerId[6] == '0' && remotePeerId[7] == '-') {
				terminate("xunlei doesn't support ltep", CloseReason.NO_LTEP);
			}
				


			if(!connectionMatches)
			{
				terminate("connction mismatch");
				return;
			}

			// start parsing BT messages
			if(incoming)
				sendBTHandshake();

			Map<String,Object> ltepHandshake = new HashMap<>();
			Map<String,Object> messages = new HashMap<>();
			if(ourListeningPort > 0)
				ltepHandshake.put("p", ourListeningPort);
			ltepHandshake.put("m", messages);
			ltepHandshake.put("v","mlDHT metadata fetcher");
			ltepHandshake.put("metadata_size", 0);
			ltepHandshake.put("reqq", 256);
			messages.put("ut_metadata", LTEP_LOCAL_META_ID);
			messages.put("ut_pex", LTEP_LOCAL_PEX_ID);
			
			// send handshake
			BEncoder encoder = new BEncoder();
			ByteBuffer handshakeBody = encoder.encode(ltepHandshake, 1024);

			ByteBuffer handshakeHeader = ByteBuffer.allocate(BT_HEADER_LENGTH + 2);
			handshakeHeader.putInt(handshakeBody.limit() + 2);
			handshakeHeader.put((byte) LTEP_HEADER_ID);
			handshakeHeader.put((byte) LTEP_HANDSHAKE_ID);
			handshakeHeader.flip();
			outputBuffers.addLast(handshakeHeader);
			outputBuffers.addLast(handshakeBody);
			
			if(remoteSupportsFastExtension) {
				ByteBuffer haveNone = ByteBuffer.allocate(5);
				haveNone.put(3, (byte) 1);
				haveNone.put(4, (byte) 0x0f);
				outputBuffers.addLast(haveNone);
			}
			/*
			if(remoteSupportsPort && dhtPort != -1) {
				ByteBuffer btPort = ByteBuffer.allocate(7);
				btPort.putInt(3);
				btPort.put((byte) 0x09);
				btPort.putShort((short) dhtPort);
				btPort.flip();
				outputBuffers.addLast(btPort);
			}*/
			canWriteEvent();

			//System.out.println("got basic handshake");

			inputBuffer.position(0);
			inputBuffer.limit(BT_HEADER_LENGTH);
			setState(EnumSet.of(STATE_BASIC_HANDSHAKING, STATE_IH_RECEIVED),STATE_LTEP_HANDSHAKING);
			return;
		}

		// parse BT header
		if(inputBuffer.limit() == BT_HEADER_LENGTH)
		{
			int msgLength = inputBuffer.getInt();

			// keepalive... wait for next msg
			if(msgLength == 0)
			{
				inputBuffer.flip();
				return;
			}
			
			if(msgLength < 0) {
				terminate("invalid message size:" + Integer.toUnsignedLong(msgLength));
				return;
			}

			int newLength = BT_HEADER_LENGTH + msgLength;

			if(newLength > inputBuffer.capacity() || newLength < 0)
			{
				terminate("message size too large or < 0");
				return;
			}

			// read payload
			inputBuffer.limit(newLength);
			return;
		}

		// skip header, we already processed that
		inputBuffer.position(4);

		// received a full message, reset timeout
		lastReceivedTime = System.currentTimeMillis();



		// read BT msg ID
		int btMsgID = inputBuffer.get() & 0xFF;
		
		//System.out.println("btmsg" + btMsgID);
		
		if(btMsgID == LTEP_HEADER_ID)
		{
			// read LTEP msg ID
			int ltepMsgID = inputBuffer.get() & 0xFF;

			if(isState(STATE_LTEP_HANDSHAKING) && ltepMsgID == LTEP_HANDSHAKE_ID)
			{
				//System.out.println("got ltep handshake");
				
				lastUsefulMessage = System.currentTimeMillis();
				
				Map<String,Object> remoteHandshake;
				try {
					remoteHandshake = ThreadLocalUtils.getDecoder().decode(inputBuffer);
				} catch (BDecodingException ex) {
					terminate("invalid bencoding in ltep handshake", CloseReason.OTHER);
					return;
				}
				
				Map<String,Object> messages = (Map<String, Object>) remoteHandshake.get("m");
				if(messages == null)
				{
					terminate("no LTEP messages defined", CloseReason.NO_META_EXCHANGE);
					return;
				}

				Long metaMsgID = (Long) messages.get("ut_metadata");
				Long pexMsgID = (Long) messages.get("ut_pex");
				Long metaLength = (Long) remoteHandshake.get("metadata_size");
				Long maxR = (Long) remoteHandshake.get("reqq");
				byte[] ver = (byte[]) remoteHandshake.get("v");
				//if(maxR != null)
					//maxRequests = maxR.intValue();
				if(ver != null)
					remoteClient = new String(ver,StandardCharsets.UTF_8);
				if(pexMsgID != null)
					ltepRemotePexId = pexMsgID.intValue();
				if(metaMsgID != null && metaLength != null)
				{
					int newInfoLength = metaLength.intValue();
					if(newInfoLength < 10) {
						terminate("indicated meta length too small to be a torrent");
						return;
					}
					
					// 30MB ought to be enough for everyone!
					if(newInfoLength > 30*1024*1024) {
						terminate("indicated meta length too large ("+newInfoLength+"), might be a resource exhaustion attack");
						return;
					}
					
					pool = poolGenerator.apply(newInfoLength);

					ltepRemoteMetadataExchangeMessageId = metaMsgID.intValue();

					setState(STATE_LTEP_HANDSHAKING,STATE_GETTING_METADATA);


					doMetaRequests();

				} else if(pexMsgID != null && keepPexOnlyOpen) {
					setState(STATE_LTEP_HANDSHAKING, STATE_PEX_ONLY);
				} else {
					terminate("no metadata exchange advertised, keep open disabled", CloseReason.NO_META_EXCHANGE);
				}
				
				if(pexMsgID == null && (metaMsgID == null || metaLength == null)){
					terminate("neither metadata exchange support nor pex detected in LTEP -> peer is useless");
					return;
				}
				
				// send 1 keep-alive
				//outputBuffers.add(ByteBuffer.wrap(new byte[4]));

			}
			
			if(!isState(STATE_LTEP_HANDSHAKING) && ltepMsgID == LTEP_LOCAL_PEX_ID) {
				BDecoder decoder = new BDecoder();
				Map<String, Object> params = decoder.decode(inputBuffer);
				
				pexConsumer.accept(AddressUtils.unpackCompact((byte[])params.get("added"), Inet4Address.class));
				pexConsumer.accept(AddressUtils.unpackCompact((byte[])params.get("added6"), Inet6Address.class));
				if(isState(STATE_PEX_ONLY))
					terminate("got 1 pex, this peer is not useful for anything else", CloseReason.OTHER);
			}

			if(isState(STATE_GETTING_METADATA) && ltepMsgID == LTEP_LOCAL_META_ID)
			{
				// consumes bytes as necessary for the bencoding
				BDecoder decoder = new BDecoder();
				Map<String, Object> params = decoder.decode(inputBuffer);
				Long type = (Long) params.get("msg_type");
				Long idx = (Long) params.get("piece");
				
				if(type == 1)
				{ // piece
					outstandingRequests--;
					chunksReceived++;
					
					ByteBuffer chunk = AnonAllocator.allocate(inputBuffer.remaining());
					chunk.put(inputBuffer);
					pool.addBuffer(idx.intValue(), chunk);
					
					lastUsefulMessage = System.currentTimeMillis();
					
					doMetaRequests();
					checkMetaRequests();
				} else if(type == 2)
				{ // reject
					pool.releasePiece(idx.intValue());
					terminate("request was rejected");
					return;
				} else if(type == 0) {
					// remote is requesting from us? makes no sense
					terminate("remote requesting metadata, but we're looking for it ourselves");
					return;
				}
			}

		}
		
		/*
		if(btMsgID == BT_BITFIELD_ID & !remoteSupportsFastExtension)
			{
				// just duplicate whatever they've sent but with 0-bits
				ByteBuffer bitfield = ByteBuffer.allocate(inputBuffer.limit());
				bitfield.putInt(bitfield.limit() - BT_HEADER_LENGTH);
				bitfield.put((byte) BT_BITFIELD_ID);
				bitfield.rewind();
				outputBuffers.addLast(bitfield);
				canWriteEvent();
			}
		*/
		
		// parse next BT header
		inputBuffer.position(0);
		inputBuffer.limit(BT_HEADER_LENGTH);
		
	}
	
	public void canReadEvent() throws IOException {
		int bytesRead = 0;
		
		if(inputBuffer == null)
		{
			inputBuffer = ByteBuffer.allocate(32 * 1024);
			// await BT handshake on first allocation since this has to be the first read
			inputBuffer.limit(20+8+20+20);
		}
		
		do {
			try
			{
				bytesRead = channel.read(inputBuffer);
			} catch (IOException e)
			{
				terminate("exception on read, cause: "+e.getMessage());
			}
			
			if(bytesRead == -1)
				terminate("reached end of stream on read");
			// message complete as far as we need it
			else if(inputBuffer.remaining() == 0)
				processInput();
		} while(bytesRead > 0 && !isState(STATE_CLOSED));
		

		
		
	}
	
	void doMetaRequests() throws IOException {
		if(!isState(STATE_GETTING_METADATA))
			return;
		
		while(outstandingRequests <= maxRequests)
		{
			int idx = pool.reservePiece(this);
			

			if(idx < 0)
				break;
			
			Map<String,Object> req = new HashMap<>();
			req.put("msg_type", 0);
			req.put("piece", idx);
			
			BEncoder encoder = new BEncoder();
			ByteBuffer body = encoder.encode(req, 512);
			ByteBuffer header = ByteBuffer.allocate(BT_HEADER_LENGTH + 1 + 1);
			header.putInt(2 + body.remaining());
			header.put((byte) LTEP_HEADER_ID);
			header.put((byte) ltepRemoteMetadataExchangeMessageId);
			header.flip();
			
			outstandingRequests++;
			
			outputBuffers.addLast(header);
			outputBuffers.addLast(body);
		}
		
		canWriteEvent();
	}
	
	void checkMetaRequests() throws IOException {
		if(pool == null)
			return;

		pool.checkComletion(infoHash);
		
		if(pool.status != Completion.PROGRESS)
			terminate("meta data exchange finished or failed");
	}


	public void canWriteEvent() throws IOException {
		try
		{
			while(!outputBuffers.isEmpty())
			{
				if(!outputBuffers.peekFirst().hasRemaining()) {
					outputBuffers.removeFirst();
					continue;
				}
				long written = channel.write(outputBuffers.toArray(new ByteBuffer[outputBuffers.size()]));
				if(written == 0) {
					// socket buffer full, update selector
					connManager.interestOpsChanged(this);
					break;
				}
			}
		} catch (IOException e)
		{
			terminate("error on write, cause: "+e.getMessage());
			return;
		}
		
		// drained queue -> update selector
		if(outputBuffers.isEmpty())
			connManager.interestOpsChanged(this);
	}
	
	public void doStateChecks(long now) throws IOException {
		// connections sharing a pool might get stalled if no more requests are left
		doMetaRequests();
		// hash check may have finished or failed due to other pool members
		checkMetaRequests();
		
		if(state.get() == STATE_CONNECTING && now - connectionOpenTime > connectTimeout) {
			terminate("connect timeout", CloseReason.CONNECT_FAILED);
			return;
		}
			
		
		long timeSinceUsefulMessage = now - lastUsefulMessage;
		long age = now - lastReceivedTime;
		
		if(age > RCV_TIMEOUT || (lastUsefulMessage > 0 && timeSinceUsefulMessage > 2 * RCV_TIMEOUT)) {
			terminate("closing idle connection "+age+" "+state+" "+outstandingRequests+" "+outputBuffers.size()+" "+inputBuffer);
		}
			
		else if(!channel.isOpen())
			terminate("async close detected");
	}
	
	public void terminate(String reasonStr, CloseReason reason)  throws IOException  {
		synchronized (this) {
			CONNECTION_STATE oldState = state.get();
			if(!setState(EnumSet.complementOf(EnumSet.of(STATE_CLOSED)), STATE_CLOSED))
				return;
			//if(!isState(STATE_CONNECTING))
				//System.out.println("closing connection for "+(infoHash != null ? new Key(infoHash).toString(false) : null)+" to "+destination+"/"+remoteClient+" state:"+state+" reason:"+reason);
			if(pool != null)
				pool.deRegister(this);

			closeReason = reason;
			if(metaHandler != null)
				metaHandler.onTerminate();
			
			channel.close();
			
			if(DHT.isLogLevelEnabled(LogLevel.Debug)) {
				String closemsg = String.format("closing pull connection inc: %b reason: %s flag: %s state: %s pid: %s fast: %b age: %d", incoming, reasonStr, reason, oldState, stripToAscii(remotePeerId), remoteSupportsFastExtension, System.currentTimeMillis() - connectionOpenTime);
				DHT.log(closemsg , LogLevel.Debug);
			}
		}
	}
	
	@Deprecated
	public void terminate(String reason) throws IOException {
		terminate(reason, CloseReason.OTHER);
	}
	
}
