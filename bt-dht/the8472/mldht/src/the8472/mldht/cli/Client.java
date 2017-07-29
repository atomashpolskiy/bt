/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.mldht.cli;

import static the8472.utils.Functional.tap;

import the8472.bencode.BDecoder;
import the8472.bencode.BEncoder;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Client {
	
	SocketChannel serverConnection;
	
	public Client(String[] args) throws Exception {
		serverConnection = SocketChannel.open(new InetSocketAddress(InetAddress.getLoopbackAddress(), Server.SERVER_PORT));
		serverConnection.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
		serverConnection.socket().setSoTimeout(0);
		
		String workDir = Paths.get("").toAbsolutePath().normalize().toString();
		
		List<byte[]> argsList = Arrays.asList(args).stream().map(s -> s.getBytes(StandardCharsets.UTF_8)).collect(Collectors.toList());
		Map<String, Object> command = new HashMap<>();
		command.put("arguments", argsList);
		command.put("cwd", workDir.getBytes(StandardCharsets.UTF_8));
		
		
		
		BEncoder encoder = new BEncoder();
		
		ByteBuffer buf = encoder.encode(command, 65535);
		
		serverConnection.write(tap(ByteBuffer.allocate(4), b -> b.putInt(0, buf.limit())));
		serverConnection.write(buf);
		//serverConnection.shutdownOutput();
		
		
		ByteBuffer header = ByteBuffer.allocate(4);
		ByteBuffer message;
		
		while(serverConnection.isOpen()) {
			header.clear();
			serverConnection.read(header);
			header.flip();

			message = ByteBuffer.allocate(header.getInt(0));
			serverConnection.read(message);
			message.flip();
			
			BDecoder dec = new BDecoder();
			
			
			Map<String,Object> msg = dec.decode(message);
			
			String action = new String((byte[])msg.get("action"), StandardCharsets.ISO_8859_1);
			
			switch (action) {
				case "sysout":
					System.out.append(new String((byte[])msg.get("payload"), StandardCharsets.UTF_8));
					break;
				case "syserr":
					System.err.append(new String((byte[])msg.get("payload"), StandardCharsets.UTF_8));
					break;
				case "exit":
					serverConnection.close();
					System.exit(((Long)msg.get("exitCode")).intValue());
					break;
				default:
					throw new IllegalStateException("unexpected action " + action);
			}
			
			
		}
	}

	public static void main(String[] args) throws Exception {
		new Client(args);
	}

}
