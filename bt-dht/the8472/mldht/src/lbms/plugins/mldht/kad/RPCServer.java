/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad;

import static the8472.bencode.Utils.prettyPrint;
import static the8472.utils.Functional.typedGet;

import the8472.bencode.Tokenizer.BDecodingException;
import the8472.bencode.Utils;
import the8472.utils.concurrent.SerializedTaskExecutor;

import lbms.plugins.mldht.kad.DHT.DHTtype;
import lbms.plugins.mldht.kad.DHT.LogLevel;
import lbms.plugins.mldht.kad.messages.ErrorMessage;
import lbms.plugins.mldht.kad.messages.ErrorMessage.ErrorCode;
import lbms.plugins.mldht.kad.messages.FindNodeResponse;
import lbms.plugins.mldht.kad.messages.MessageBase;
import lbms.plugins.mldht.kad.messages.MessageBase.Method;
import lbms.plugins.mldht.kad.messages.MessageBase.Type;
import lbms.plugins.mldht.kad.messages.MessageDecoder;
import lbms.plugins.mldht.kad.messages.MessageException;
import lbms.plugins.mldht.kad.messages.PingRequest;
import lbms.plugins.mldht.kad.messages.PingResponse;
import lbms.plugins.mldht.kad.utils.AddressUtils;
import lbms.plugins.mldht.kad.utils.ByteWrapper;
import lbms.plugins.mldht.kad.utils.ResponseTimeoutFilter;
import lbms.plugins.mldht.kad.utils.ThreadLocalUtils;
import lbms.plugins.mldht.utils.ExponentialWeightendMovingAverage;
import lbms.plugins.mldht.utils.NIOConnectionManager;
import lbms.plugins.mldht.utils.Selectable;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Formatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * @author The_8472, Damokles
 *
 */
public class RPCServer {
	
	private static final int MTID_LENGTH = 6;
	
	public enum State {
		INITIAL,
		RUNNING,
		STOPPED
	}
	
	
	private State									state = State.INITIAL;
	private InetAddress								addr;
	private DHT										dh_table;
	private RPCServerManager						manager;
	private ConcurrentMap<ByteWrapper, RPCCall>		calls;
	private Queue<RPCCall>							call_queue;
	private Queue<EnqueuedSend>						pipeline;
	private volatile int							numReceived;
	private volatile int							numSent;
	private int										port;
	private Instant									startTime;
	private RPCStats								stats;
	// keeps track of RTT histogram for nodes not in our routing table
	private ResponseTimeoutFilter					timeoutFilter;
	private Key										derivedId;
	private InetSocketAddress						consensusExternalAddress;
	private SpamThrottle 							throttle = new SpamThrottle();
	private SpamThrottle 							requestThrottle;
	private ExponentialWeightendMovingAverage		unverifiedLossrate = new ExponentialWeightendMovingAverage().setWeight(0.01).setValue(0.5);
	private ExponentialWeightendMovingAverage		verifiedEntryLossrate = new ExponentialWeightendMovingAverage().setWeight(0.01).setValue(0.5);
	
	private LinkedHashMap<InetAddress, InetSocketAddress> originPairs  = new LinkedHashMap<InetAddress, InetSocketAddress>(64, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<InetAddress,InetSocketAddress> eldest) {
			if(this.size() > 64)
				return true;
			return false;
		};
	};
	
	
	private volatile boolean isReachable = false;
	private int		numReceivesAtLastCheck = 0;
	private long	timeOfLastReceiveCountChange = 0;
	

	SocketHandler sel;

	public RPCServer (RPCServerManager manager, InetAddress addr, int port, RPCStats stats) {
		this.port = port;
		this.dh_table = manager.dht;
		timeoutFilter = new ResponseTimeoutFilter();
		pipeline = new ConcurrentLinkedQueue<>();
		calls = new ConcurrentHashMap<>(DHTConstants.MAX_ACTIVE_CALLS);
		call_queue = new ConcurrentLinkedQueue<>();
		this.stats = stats;
		this.addr = addr;
		this.manager = manager;
		// reserve an ID
		derivedId = dh_table.getNode().registerId();
		sel = new SocketHandler();
	}
	
	public DHT getDHT()
	{
		return dh_table;
	}
	
	public int getPort() {
		return port;
	}
	
	public InetAddress getBindAddress() {
		return addr;
	}
	
	/**
	 * @return external addess, if known (only ipv6 for now)
	 */
	public InetAddress getPublicAddress() {
		if(sel == null)
			return null;
		SelectableChannel chan = sel.getChannel();
		if(chan == null)
			return null;
		
		InetAddress addr = ((DatagramChannel)chan).socket().getLocalAddress();
		if(dh_table.getType().PREFERRED_ADDRESS_TYPE.isInstance(addr) && AddressUtils.isGlobalUnicast(addr))
			return addr;
		return null;
	}

	
	public Key getDerivedID() {
		return derivedId;
	}
	
	void setOutgoingThrottle(SpamThrottle throttle) {
		this.requestThrottle = throttle;
	}
	
	public SpamThrottle getRequestThrottle() {
		return this.requestThrottle;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see lbms.plugins.mldht.kad.RPCServerBase#start()
	 */
	public void start() {
		if(state != State.INITIAL)
			throw new IllegalStateException("already initialized");
		startTime = Instant.now();
		state = State.RUNNING;
		DHT.logInfo("Starting RPC Server");
		sel.start();
		
	}
	
	public State getState() {
		return state;
	}
	
	public void stop() {
		if(state == State.STOPPED)
			return;
		state = State.STOPPED;
		
		try
		{
			sel.close();
		} catch (IOException e)
		{
			DHT.log(e, LogLevel.Error);
		}
		dh_table.getNode().removeId(derivedId);
		manager.serverRemoved(this);
		pipeline.clear();
	}
	
	
	Collection<Consumer<RPCCall>> enqueueEventConsumers = new CopyOnWriteArrayList<>();


	/* (non-Javadoc)
	 * @see lbms.plugins.mldht.kad.RPCServerBase#doCall(lbms.plugins.mldht.kad.messages.MessageBase)
	 */
	public void doCall(RPCCall c) {
		
		MessageBase req = c.getRequest();
		if(req.getServer() == null)
			req.setServer(this);
		
		enqueueEventConsumers.forEach(callback -> callback.accept(c));
		
		call_queue.add(c);
		
		drainTrigger.run();
	}
	
	Runnable drainTrigger = SerializedTaskExecutor.onceMore(this::drainQueue);
	
	private void drainQueue() {
		
		int capacity = DHTConstants.MAX_ACTIVE_CALLS  - calls.size();
		
		requestThrottle.decay();
		
		while(capacity > 0) {
			
			RPCCall c = call_queue.poll();

			if(c == null) {
				Runnable r = awaitingDeclog.poll();
				if(r != null) {
					r.run();
					continue;
				}
				break;
			}
			
			int delay = requestThrottle.calculateDelayAndAdd(c.getRequest().getDestination().getAddress());


			if(delay > 0) {
				delay += ThreadLocalRandom.current().nextInt(30, 50);
				DHT.logInfo("Queueing RPCCall (+"+delay+"ms), would be spamming remote peer " + c.getExpectedID() + " " + c.knownReachableAtCreationTime() + " " +  AddressUtils.toString(c.getRequest().getDestination()) + " " + c.getRequest().toString());
				dh_table.getScheduler().schedule(() -> {
					call_queue.add(c);
					drainTrigger.run();
					requestThrottle.saturatingDec(c.getRequest().getDestination().getAddress());
				}, delay, TimeUnit.MILLISECONDS);
				continue;
			}
			
			byte[] mtid = new byte[MTID_LENGTH];
			ThreadLocalUtils.getThreadLocalRandom().nextBytes(mtid);

			if(calls.putIfAbsent(new ByteWrapper(mtid),c) == null)
			{
				capacity--;
				dispatchCall(c, mtid);
			} else {
				// this is very unlikely to happen
				call_queue.add(c);
			}
		}
	}
	
	public void onEnqueue(Consumer<RPCCall> listener) {
		enqueueEventConsumers.add(listener);
	}
	
	private final RPCCallListener rpcListener = new RPCCallListener() {
		
		public void onTimeout(RPCCall c) {
			ByteWrapper w = new ByteWrapper(c.getRequest().getMTID());
			stats.addTimeoutMessageToCount(c.getRequest());
			if(c.knownReachableAtCreationTime())
				verifiedEntryLossrate.updateAverage(1.0);
			else
				unverifiedLossrate.updateAverage(1.0);
			calls.remove(w, c);
			dh_table.timeout(c);
			drainTrigger.run();
		}
		
		public void onStall(RPCCall c) {}
		public void onResponse(RPCCall c, MessageBase rsp) {
			if(c.knownReachableAtCreationTime())
				verifiedEntryLossrate.updateAverage(0.0);
			else
				unverifiedLossrate.updateAverage(0.0);
		}
	};
	
	/* (non-Javadoc)
	 * @see lbms.plugins.mldht.kad.RPCServerBase#ping(lbms.plugins.mldht.kad.Key, java.net.InetSocketAddress)
	 */
	public void ping (InetSocketAddress addr) {
		PingRequest pr = new PingRequest();
		pr.setID(derivedId);
		pr.setDestination(addr);
		doCall(new RPCCall(pr));
	}

	/* (non-Javadoc)
	 * @see lbms.plugins.mldht.kad.RPCServerBase#findCall(byte)
	 */
	public RPCCall findCall (byte[] mtid) {
		return calls.get(new ByteWrapper(mtid));
	}

	/// Get the number of active calls
	/* (non-Javadoc)
	 * @see lbms.plugins.mldht.kad.RPCServerBase#getNumActiveRPCCalls()
	 */
	public int getNumActiveRPCCalls () {
		return calls.size();
	}

	/**
	 * @return the numReceived
	 */
	public int getNumReceived () {
		return numReceived;
	}

	/**
	 * @return the numSent
	 */
	public int getNumSent () {
		return numSent;
	}

	/* (non-Javadoc)
	 * @see lbms.plugins.mldht.kad.RPCServerBase#getStats()
	 */
	public RPCStats getStats () {
		return stats;
	}
	
	public void checkReachability(long now) {
		// don't do pings too often if we're not receiving anything (connection might be dead)
		if(numReceived != numReceivesAtLastCheck)
		{
			isReachable = true;
			timeOfLastReceiveCountChange = now;
			numReceivesAtLastCheck = numReceived;
		} else if(now - timeOfLastReceiveCountChange > DHTConstants.REACHABILITY_TIMEOUT)
		{
			isReachable = false;
			timeoutFilter.reset();
		}
	}
	
	public boolean isReachable() {
		return isReachable;
	}
	
	private void handlePacket (ByteBuffer p, SocketAddress soa) {
		InetSocketAddress source = (InetSocketAddress) soa;
		int rawLength = p.remaining();
		
		// ignore port 0, can't respond to them anyway and responses to requests from port 0 will be useless too
		if(source.getPort() == 0)
			return;



		
		Map<String, Object> bedata = null;
		MessageBase msg = null;
		
		try {
			bedata = ThreadLocalUtils.getDecoder().decode(p);
			
			try {
				if (DHT.isLogLevelEnabled(LogLevel.Verbose)) {
					DHT.logVerbose("received: " + Utils.prettyPrint(bedata) + " from: " + source + " length: "+rawLength );
				}
			} catch (Exception e) {
				DHT.log(e, LogLevel.Error);
			}
		} catch(BDecodingException e) {
			p.rewind();
			DHT.logInfo("failed to decode message  " + Utils.stripToAscii(p) + " (length:"+p.remaining()+") from: " + source + " reason:" + e.getMessage());
			MessageBase err = new ErrorMessage(new byte[] {0,0,0,0}, ErrorCode.ProtocolError.code,"invalid bencoding: "+e.getMessage());
			err.setDestination(source);
			sendMessage(err);
			return;
		} catch(Exception e) {
			DHT.log(e, LogLevel.Error);
			p.rewind();
			DHT.logError("unexpected error while bdecoding message  " + Utils.stripToAscii(p) + " (length:"+p.remaining()+") from: " + source + " reason:" + e.getMessage());
			return;
		}
		
		try {
			MessageDecoder dec = new MessageDecoder((byte[] mtid) -> {
				return Optional.ofNullable(findCall(mtid)).map(RPCCall::getMessageMethod);
			}, dh_table.getType());
			
			p.rewind();
			dec.toDecode(p, bedata);
			msg = dec.parseMessage();
		} catch(MessageException e)
		{
			byte[] mtid = typedGet(bedata, MessageBase.TRANSACTION_KEY, byte[].class).orElse(new byte[MTID_LENGTH]);
			Method m = typedGet(bedata, MessageBase.Type.TYPE_KEY, byte[].class).map(b -> new String(b, StandardCharsets.ISO_8859_1)).map(MessageBase.messageMethod::get).orElse(Method.UNKNOWN);
			DHT.log(e.getMessage(), LogLevel.Debug);
			ErrorMessage err = new ErrorMessage(mtid, e.errorCode.code,e.getMessage());
			err.setDestination(source);
			err.setMethod(m);
			sendMessage(err);
			return;
		} catch(IOException e) {
			DHT.log(e, LogLevel.Error);
		}
		
		if(msg == null)
			return;
		
		if(DHT.isLogLevelEnabled(LogLevel.Debug))
			DHT.logDebug("RPC received message from "+source.getAddress().getHostAddress() + ":" + source.getPort() +" | "+msg.toString()+ " length: "+rawLength );
		stats.addReceivedMessageToCount(msg);
		msg.setOrigin(source);
		msg.setServer(this);
		
		// just respond to incoming requests, no need to match them to pending requests
		if(msg.getType() == Type.REQ_MSG) {
			handleMessage(msg);
			return;
		}
		
		
		if(msg.getType() == Type.RSP_MSG && msg.getMTID().length != MTID_LENGTH) {
			byte[] mtid = msg.getMTID();
			DHT.logDebug("response with invalid mtid length received: "+ Utils.prettyPrint(mtid));
			ErrorMessage err = new ErrorMessage(mtid, ErrorCode.ServerError.code, "received a response with a transaction id length of "+mtid.length+" bytes, expected [implementation-specific]: "+MTID_LENGTH+" bytes");
			err.setDestination(msg.getOrigin());
			sendMessage(err);
			return;
		}
		
			
		
		// check if this is a response to an outstanding request
		RPCCall c = calls.get(new ByteWrapper(msg.getMTID()));
		
		// message matches transaction ID and origin == destination
		if(c != null) {
										
			if(c.getRequest().getDestination().equals(msg.getOrigin())) {
				// remove call first in case of exception
				if(calls.remove(new ByteWrapper(msg.getMTID()),c)) {
					msg.setAssociatedCall(c);
					c.response(msg);

					drainTrigger.run();
					// apply after checking for a proper response
					handleMessage(msg);
				}
				
				return;
			}
			
			// 1. the message is not a request
			// 2. transaction ID matched
			// 3. request destination did not match response source!!
			// 4. we're using random 48 bit MTIDs
			// this happening by chance is exceedingly unlikely
			
			// indicates either port-mangling NAT, a multhomed host listening on any-local address or some kind of attack
			// -> ignore response
			
			DHT.logError("mtid matched, socket address did not, ignoring message, request: " + c.getRequest().getDestination() + " -> response: " + msg.getOrigin() + " v:"+ msg.getVersion().map(Utils::prettyPrint).orElse(""));
			if(msg.getType() != MessageBase.Type.ERR_MSG && dh_table.getType() == DHTtype.IPV6_DHT) {
				// this is more likely due to incorrect binding implementation in ipv6. notify peers about that
				// don't bother with ipv4, there are too many complications
				MessageBase err = new ErrorMessage(msg.getMTID(), ErrorCode.GenericError.code, "A request was sent to " + c.getRequest().getDestination() + " and a response with matching transaction id was received from " + msg.getOrigin() + " . Multihomed nodes should ensure that sockets are properly bound and responses are sent with the correct source socket address. See BEPs 32 and 45.");
				err.setDestination(c.getRequest().getDestination());
				sendMessage(err);
			}
			
			// but expect an upcoming timeout if it's really just a misbehaving node
			c.setSocketMismatch();
			c.injectStall();
			
			return;
		}
		
		// a) it's a response b) didn't find a call c) uptime is high enough that it's not a stray from a restart
		// -> did not expect this response
		if (msg.getType() == Type.RSP_MSG && Duration.between(startTime, Instant.now()).getSeconds() > 2*60) {
			byte[] mtid = msg.getMTID();
			DHT.logDebug("Cannot find RPC call for response: "+ Utils.prettyPrint(mtid));
			ErrorMessage err = new ErrorMessage(mtid, ErrorCode.ServerError.code, "received a response message whose transaction ID did not match a pending request or transaction expired");
			err.setDestination(msg.getOrigin());
			sendMessage(err);
			return;
		}

		if (msg.getType() == Type.ERR_MSG) {
			handleMessage(msg);
			return;
		}
		
		DHT.logError("not sure how to handle message " + msg);
	}
	
	private void handleMessage(MessageBase msg) {
		if(msg.getType() == Type.RSP_MSG && msg.getPublicIP() != null)
			updatePublicIPConsensus(msg.getOrigin().getAddress(), msg.getPublicIP());
		dh_table.incomingMessage(msg);
		msg.apply(dh_table);
	}
	
	private void updatePublicIPConsensus(InetAddress source, InetSocketAddress addr) {
		if(!AddressUtils.isGlobalUnicast(addr.getAddress()))
			return;
		synchronized (originPairs) {
			originPairs.put(source, addr);
			if(originPairs.size() > 20) {
				originPairs.values().stream().collect(Collectors.groupingBy(o -> o, Collectors.counting())).entrySet().stream().max((a,b) -> (int)(a.getValue() - b.getValue())).ifPresent(e -> setConsensusAddress(e.getKey()));
			}
		}
				
	}
	
	private void setConsensusAddress(InetSocketAddress addr) {
		consensusExternalAddress = addr;
	}
	
	public InetSocketAddress getConsensusExternalAddress() {
		return consensusExternalAddress;
	}
	
	public Optional<InetAddress> getCombinedPublicAddress() {
		InetAddress fromSocket = getPublicAddress();
		if(fromSocket != null)
			return Optional.of(fromSocket);
		return Optional.ofNullable(getConsensusExternalAddress()).map(InetSocketAddress::getAddress);
	}
	
	Queue<Runnable> awaitingDeclog = new ConcurrentLinkedQueue<>();
	
	public void onDeclog(Runnable r) {
		awaitingDeclog.add(r);
	}

	private void fillPipe(EnqueuedSend es) {
		pipeline.add(es);
		sel.writeEvent();
	}
		

	private void dispatchCall(RPCCall call, byte[] mtid)
	{
		MessageBase msg = call.getRequest();
		msg.setMTID(mtid);
		call.addListener(rpcListener);
		
		// known nodes - routing table entries - keep track of their own RTTs
		// they are also biased towards lower RTTs compared to the general population encountered during regular lookups
		// don't let them skew the measurement of the general node population
		if(!call.knownReachableAtCreationTime())
			timeoutFilter.registerCall(call);
		
		EnqueuedSend es = new EnqueuedSend(msg, call);
		fillPipe(es);
	}

	/* (non-Javadoc)
	 * @see lbms.plugins.mldht.kad.RPCServerBase#sendMessage(lbms.plugins.mldht.kad.messages.MessageBase)
	 */
	public void sendMessage (MessageBase msg) {
		if(msg.getDestination() == null)
			throw new IllegalArgumentException("message destination must not be null");
		fillPipe(new EnqueuedSend(msg, null));
	}
	
	public ResponseTimeoutFilter getTimeoutFilter() {
		return timeoutFilter;
	}

	/*
	private void send (InetSocketAddress addr, byte[] msg) throws IOException {
		if (!sock.isClosed()) {
			DatagramPacket p = new DatagramPacket(msg, msg.length);
			p.setSocketAddress(addr);
			try
			{
				sock.send(p);
			} catch (IOException e)
			{
				if(sock.isClosed() || NetworkInterface.getByInetAddress(sock.getLocalAddress()) == null)
				{
					createSocket();
					sock.send(p);
				} else
				{
					throw e;
				}
			}

		}
	}*/

	@Override
	public String toString() {
		Formatter f = new Formatter();
		
		f.format("%s\tbind: %s consensus: %s%n", getDerivedID(), getBindAddress(), consensusExternalAddress);
		f.format("rx: %d tx: %d active: %d baseRTT: %d loss: %f  loss (verified): %f uptime: %s%n",
				numReceived, numSent, getNumActiveRPCCalls(), timeoutFilter.getStallTimeout(), unverifiedLossrate.getAverage(), verifiedEntryLossrate.getAverage() , age());
		f.format("RTT stats (%dsamples) %s", timeoutFilter.getSampleCount(), timeoutFilter.getCurrentStats());

		return f.toString();
	}
	
	Duration age() {
		Instant start = startTime;
		if(start == null)
			return Duration.ZERO;
		return Duration.between(start, Instant.now());
	}
	
	static final ThreadLocal<ByteBuffer> writeBuffer = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(1500));
	static final ThreadLocal<ByteBuffer> readBuffer = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(DHTConstants.RECEIVE_BUFFER_SIZE));
	
	class SocketHandler implements Selectable {
		DatagramChannel channel;
		
		private static final int NOT_INITIALIZED = -2;
		private static final int INITIALIZING = -1;
		private static final int WRITE_STATE_IDLE = 0;
		private static final int WRITE_STATE_WRITING = 2;
		private static final int WRITE_STATE_AWAITING_NIO_NOTIFICATION = 3;
		private static final int CLOSED = 4;
		
		private final AtomicInteger writeState = new AtomicInteger(NOT_INITIALIZED);
		NIOConnectionManager connectionManager;
		
		public SocketHandler() {

		}
		
		void start() {
			if(!writeState.compareAndSet(NOT_INITIALIZED, INITIALIZING)) {
				return;
			}
			
			try
			{
				timeoutFilter.reset();
	
				channel = DatagramChannel.open(dh_table.getType().PROTO_FAMILY);
				channel.configureBlocking(false);
				channel.setOption(StandardSocketOptions.SO_RCVBUF, 2*1024*1024);
				channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
				channel.bind(new InetSocketAddress(addr, port));
				connectionManager = dh_table.getConnectionManager();
				connectionManager.register(this);
				if(!writeState.compareAndSet(INITIALIZING, WRITE_STATE_IDLE)) {
					writeState.set(INITIALIZING);
					close();
					
				}
			} catch (IOException e)
			{
				e.printStackTrace();
			}
		}
		
		
		

		
		@Override
		public void selectionEvent(SelectionKey key) throws IOException {
			// schedule async writes first before spending thread time on reads
			if(key.isValid() && key.isWritable()) {
				writeState.set(WRITE_STATE_IDLE);
				connectionManager.interestOpsChanged(this);
				dh_table.getScheduler().execute(this::writeEvent);
			}
			if(key.isValid() && key.isReadable())
				readEvent();
				
		}
		
		void readEvent() throws IOException {
			
			throttle.decay();
			
			ByteBuffer readBuffer = RPCServer.readBuffer.get();
			
			while(true)
			{
				readBuffer.clear();
				InetSocketAddress soa =  (InetSocketAddress) channel.receive(readBuffer);
				if(soa == null)
					break;
				
				// * no conceivable DHT message is smaller than 10 bytes
				// * all DHT messages start with a 'd' for dictionary
				// * port 0 is reserved
				// -> immediately discard junk on the read loop, don't even allocate a buffer for it
				if(readBuffer.position() < 10 || readBuffer.get(0) != 'd' || soa.getPort() == 0)
					continue;
				if(throttle.addAndTest(soa.getAddress()))
					continue;
				
				// copy from the read buffer since we hand off to another thread
				readBuffer.flip();
				ByteBuffer buf = ByteBuffer.allocate(readBuffer.limit()).put(readBuffer);
				buf.flip();
				
				dh_table.getScheduler().execute(() -> {handlePacket(buf, soa);});
				numReceived++;
				stats.addReceivedBytes(buf.limit() + dh_table.getType().HEADER_LENGTH);
			}
		}
		
		public void writeEvent()
		{
			// simply assume nobody else is writing and attempt to do it
			// if it fails it's the current writer's job to double-check after releasing the write lock
			int currentState = WRITE_STATE_IDLE;
			
			if(writeState.compareAndSet(currentState, WRITE_STATE_WRITING))
			{
				// we are now the exclusive writer for this socket
				
				while(true)
				{
					EnqueuedSend es = pipeline.poll();
					if(es == null)
						break;
					try
					{
						
						
						ByteBuffer buf = writeBuffer.get();
								
						es.encodeTo(buf);
						
						int bytesSent = channel.send(buf, es.toSend.getDestination());
						
						if(bytesSent == 0)
						{
							pipeline.add(es);

							writeState.set(WRITE_STATE_AWAITING_NIO_NOTIFICATION);
							// wakeup -> updates selections -> will wait for write OP
							connectionManager.interestOpsChanged(this);
							
							return;
						}
						
						if(DHT.isLogLevelEnabled(LogLevel.Verbose)) {
							DHT.logVerbose("sent: " + prettyPrint(es.toSend.getBase())+ " to " + es.toSend.getDestination());
						}
						
						if(es.associatedCall != null) {
							es.associatedCall.sent(RPCServer.this);
							// when we send requests to a node we don't want their replies to get stuck in the filter
							throttle.remove(es.toSend.getDestination().getAddress());
						}
							
						
						stats.addSentMessageToCount(es.toSend);
						stats.addSentBytes(bytesSent + dh_table.getType().HEADER_LENGTH);
						if(DHT.isLogLevelEnabled(LogLevel.Debug))
							DHT.logDebug("RPC send message to " + es.toSend.getDestination() + " | "+ es.toSend.toString() + " | length: " +bytesSent);
					} catch (IOException e)
					{
						// async close
						if(!channel.isOpen())
							return;
						
						// BSD variants may throw an exception (ENOBUFS) instead of just signaling 0 bytes sent when network queues are full -> back off just like we would in the 0 bytes case.
						if(e.getMessage().equals("No buffer space available")) {
							pipeline.add(es);
							writeState.set(WRITE_STATE_AWAITING_NIO_NOTIFICATION);
							connectionManager.interestOpsChanged(this);

							return;
						}

						DHT.log(new IOException(addr+" -> "+es.toSend.getDestination()+" while attempting to send "+es.toSend, e), LogLevel.Error);
						if(es.associatedCall != null)
						{ // need to notify listeners
							es.associatedCall.sendFailed();
						}
						break;
					}
					
					numSent++;
				}
				
				// release claim on the socket
				writeState.compareAndSet(WRITE_STATE_WRITING, WRITE_STATE_IDLE);
				
				// check if we might have to pick it up again due to races
				// schedule async to avoid infinite stacks
				if(pipeline.peek() != null)
					dh_table.getScheduler().execute(this::writeEvent);

			
			}
			
	
		}
		
		@Override
		public void registrationEvent(NIOConnectionManager manager, SelectionKey key) throws IOException {
		}
		
		@Override
		public SelectableChannel getChannel() {
			return channel;
		}
		
		public void close() throws IOException {
			if(writeState.get() == CLOSED)
				return;
			writeState.set(CLOSED);
			stop();
			if(channel != null)
				channel.close();
		}
		
		@Override
		public void doStateChecks(long now) throws IOException {
			if(!channel.isOpen() || channel.socket().isClosed())
			{
				close();
				return;
			}
		}
		
		public int calcInterestOps() {
			int ops = SelectionKey.OP_READ;
			if(writeState.get() == WRITE_STATE_AWAITING_NIO_NOTIFICATION)
				ops |= SelectionKey.OP_WRITE;
			return ops;
		}
	}

	private class EnqueuedSend {
		MessageBase toSend;
		RPCCall associatedCall;
		
		public EnqueuedSend(MessageBase msg, RPCCall call) {
			toSend = msg;
			associatedCall = call;
			assert(toSend.getDestination() != null);
			decorateMessage();
		}
		
		private void decorateMessage() {
			if(toSend.getID() == null)
				toSend.setID(getDerivedID());
			
			// don't include IP on GET_PEER responses, they're already pretty heavy-weight
			if((toSend instanceof PingResponse || toSend instanceof FindNodeResponse) && toSend.getPublicIP() == null) {
				toSend.setPublicIP(toSend.getDestination());
			}
			
			if(associatedCall != null) {
				long configuredRTT = associatedCall.getExpectedRTT();

				if(configuredRTT == -1) {
					configuredRTT = timeoutFilter.getStallTimeout();
				}

// TODO: re-evaluate necessity
//				/*
//				use less aggressive stall timeouts when we observe a high percentage of RPC calls timeouts
//				high loss rates may indicate congested links or saturated NAT
//
//				minimum thresholds based on measurements on a server-class nodes.
//				average observed timeout rate
//				- for non-verified contacts  ~50%
//				- for verified contacts ~15%
//
//				 */
//
//				double adjustedLossrate = Math.max(0, unverifiedLossrate.getAverage() - 0.5) * 2.;
//				double adjustedVerifiedLossrate = Math.max(0, verifiedEntryLossrate.getAverage() - 1./3.) * 3./2.;
//
//				double correctionFactor = Math.max(adjustedLossrate, adjustedVerifiedLossrate);
//
//
//
//				long diff = DHTConstants.RPC_CALL_TIMEOUT_MAX - configuredRTT;
//
//
//				associatedCall.setExpectedRTT((long) (configuredRTT + diff * correctionFactor));
				
				associatedCall.setExpectedRTT(configuredRTT);
			}
				
		}
		
		void encodeTo(ByteBuffer buf) throws IOException {
			try {
				buf.rewind();
				buf.limit(dh_table.getType().MAX_PACKET_SIZE);
				toSend.encode(buf);
			} catch (Exception e) {
				ByteBuffer t = ByteBuffer.allocate(4096);
				try {
					toSend.encode(t);
				} catch(Exception e2) {
					
				}
				
				DHT.logError("encode failed for " + toSend.toString() + " 2nd encode attempt: (" + t.limit() + ") bytes. base map was:" + Utils.prettyPrint(toSend.getBase())  );
				
				
				throw new IOException(e) ;
			}
		}
	}
	
}
