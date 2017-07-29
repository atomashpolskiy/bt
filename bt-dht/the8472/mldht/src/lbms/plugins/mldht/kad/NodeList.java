/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad;

import static the8472.utils.Functional.unchecked;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import lbms.plugins.mldht.kad.DHT.DHTtype;
import the8472.bencode.BEncoder.StringWriter;

public interface NodeList {
	
	enum AddressType {
		V4, V6;
	}
	
	AddressType type();
	
	Stream<KBucketEntry> entries();
	
	int packedSize();
	
	default StringWriter writer() {
		return new StringWriter() {
			
			@Override
			public void writeTo(ByteBuffer buf) {
				entries().forEach(e -> {
					
					InetSocketAddress sockAddr = e.getAddress();
					InetAddress addr = sockAddr.getAddress();
					buf.put(e.getID().hash);
					buf.put(addr.getAddress());
					buf.putShort((short) sockAddr.getPort());
				});
			}
			
			@Override
			public int length() {
				return packedSize();
			}
		};
	}
	
	static NodeList fromBuffer(ByteBuffer src, AddressType type) {
		Objects.requireNonNull(src);
		Objects.requireNonNull(type);
		
		return new NodeList() {
			
			@Override
			public int packedSize() {
				return src.remaining();
			}
			
			@Override
			public Stream<KBucketEntry> entries() {
				ByteBuffer buf = src.slice();
				
				byte[] rawId = new byte[20];
				byte[] rawAddr = new byte[type == AddressType.V4 ? 4 : 16];
				
				return IntStream.range(0, packedSize()/(type == AddressType.V4 ? DHTtype.IPV4_DHT.NODES_ENTRY_LENGTH : DHTtype.IPV6_DHT.NODES_ENTRY_LENGTH)).mapToObj(i -> {
					
					buf.get(rawId);
					buf.get(rawAddr);
					int port = Short.toUnsignedInt(buf.getShort());
					
					InetAddress addr = unchecked(() -> InetAddress.getByAddress(rawAddr));
					Key id = new Key(rawId);
					
					return new KBucketEntry(unchecked(() -> new InetSocketAddress(addr, port)), id);
				});
			}
			
			@Override
			public StringWriter writer() {
				return new StringWriter() {
					
					@Override
					public void writeTo(ByteBuffer buf) {
						buf.put(src.slice());
					}
					
					@Override
					public int length() {
						return src.remaining();
					}
				};
			}

			@Override
			public AddressType type() {
				return type;
			}
		};
	}

}
