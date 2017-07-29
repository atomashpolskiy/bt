/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.bt;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import the8472.bencode.BDecoder;
import the8472.bencode.BEncoder;
import the8472.bencode.PathMatcher;
import the8472.bencode.Tokenizer;

import lbms.plugins.mldht.kad.Key;
import lbms.plugins.mldht.kad.utils.ThreadLocalUtils;

public class TorrentUtils {
	
	public static ByteBuffer wrapBareInfoDictionary(ByteBuffer dict) {
		Map<String, Object> root = new HashMap<>();
		
		root.put("info", new BEncoder.RawData(dict));
		
		BEncoder encoder = new BEncoder();
		return encoder.encode(root, dict.remaining() + 100);
	}
	
	public static Optional<String> getTorrentName(ByteBuffer torrent) {
		BDecoder decoder = new BDecoder();
		Map<String, Object> root = decoder.decode(torrent.duplicate());
		
		return Optional.ofNullable((Map<String, Object>)root.get("info")).map(info -> {
			return Optional.ofNullable((byte[])info.get("name.utf-8")).orElse((byte[])info.get("name"));
		}).map(bytes -> new String(bytes, StandardCharsets.UTF_8));
	}
	
	public static String magnet(ByteBuffer rawTorrent) {
		Key ih = infohash(rawTorrent);
		
		return "magnet:?xt=urn:btih:" + ih.toString(false);
	}
	
	public static Key infohash(ByteBuffer rawTorrent) {
		
		Tokenizer t = new Tokenizer();
		PathMatcher m = new PathMatcher("info");
		m.tokenizer(t);
		
		ByteBuffer rawInfo = m.match(rawTorrent.duplicate());
		
		MessageDigest dig = ThreadLocalUtils.getThreadLocalSHA1();
		dig.reset();
		dig.update(rawInfo);
		return new Key(dig.digest());
		
	}
	

}
