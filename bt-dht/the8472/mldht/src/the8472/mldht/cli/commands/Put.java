/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.mldht.cli.commands;

import the8472.mldht.cli.CommandProcessor;
import the8472.mldht.cli.ParseArgs;

import lbms.plugins.mldht.kad.DHT;
import lbms.plugins.mldht.kad.GenericStorage;
import lbms.plugins.mldht.kad.GenericStorage.StorageItem;
import lbms.plugins.mldht.kad.Key;
import lbms.plugins.mldht.kad.tasks.GetLookupTask;
import lbms.plugins.mldht.kad.tasks.PutTask;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec;

public class Put extends CommandProcessor {
	
	StorageItem it;
	boolean mutable;
	Path keyFile;
	EdDSAPrivateKey key;
	Object data;
	byte[] salt;

	@Override
	protected void process() {
		
		try {
			
			keyFile = ParseArgs.extractString(arguments, "-keyfile").map(Paths::get).orElse(null);
			salt = ParseArgs.extractString(arguments, "-salt").map(s -> s.getBytes(StandardCharsets.UTF_8)).orElse(null);
			
			mutable = keyFile != null || salt != null;
			
			loadKey();
			loadData();
			
			// TODO: sequence number
			if(mutable) {
				it = GenericStorage.buildMutable(data, key, salt, 1);
				assert(it.validateSig());
			} else {
				it = GenericStorage.buildImmutable(data);
			}
			
		} catch(Exception e) {
			handleException(e);
			return;
		}
		
		startLookup();
	}
	
	void loadData() throws IOException {
		Path dataFile = ParseArgs.extractString(arguments, "-f").map(Paths::get).orElse(null);
		
		if(dataFile != null) {
			data = Files.readAllBytes(dataFile);
		} else {
			data = arguments.get(0);
		}
		
		
		
	}
	
	void loadKey() throws IOException, NoSuchAlgorithmException {
		if(!mutable)
			return;
		
		if(keyFile == null) {
			keyFile = Paths.get(".", ".keys", "default.priv");
		}
		
		keyFile = keyFile.toAbsolutePath().normalize();
		
		Path dir = keyFile.getParent();
		
		Files.createDirectories(dir);
		
		// TODO: platform detection
		try {
			Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwx------"));
		} catch (UnsupportedOperationException ex) {
			printErr("Warning: could not restrict access for private key storage directory (filesystem does not support posix permissions?). " + dir.toString() + "\n");
		}
		
		byte[] seed;
		
	
		if(!Files.exists(keyFile)) {
			seed = SecureRandom.getInstanceStrong().generateSeed(32);
			Files.write(keyFile, Base64.getEncoder().encode(seed), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
			println("Key does not exist, creating... saving at " + keyFile.toString());
		} else {
			seed = Base64.getDecoder().decode(Files.readAllBytes(keyFile));
			if(seed.length != 32) {
				throw new IllegalArgumentException("failed to decode private key, expected 32bytes after base64 decoding");
			}
		}
		
		key = new EdDSAPrivateKey(new EdDSAPrivateKeySpec(seed, GenericStorage.StorageItem.spec));
	}
	
	void startLookup() {
		
		Key k = it.fingerprint();
		
		println(k.toString(false));
		
		AtomicInteger completionCounter = new AtomicInteger();
		
		dhts.stream().filter(DHT::isRunning).map(d -> d.getServerManager().getRandomActiveServer(false)).filter(Objects::nonNull).forEach(s -> {
			GetLookupTask g = new GetLookupTask(k, s, s.getDHT().getNode());
			
			g.expectedSalt(salt);
			
			g.addListener(t -> {
				
				PutTask p = new PutTask(s, s.getDHT().getNode(), g.getTokens(), it);
				
				p.addListener(t2 -> {
					println(t2.getRPC().getDHT().getType()+": stored on "+t2.getRecvResponses()+" nodes");
					if(completionCounter.decrementAndGet() == 0)
						exit(0);
					
				});
				
				s.getDHT().getTaskManager().addTask(p);
				
				
				
			});
			
			s.getDHT().getTaskManager().addTask(g);
			
			completionCounter.incrementAndGet();
		});
	}

}
