/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.utils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class AnonAllocator {

	static final boolean MAP_AND_UNLINK_SUPPORTED;

	static {
		Path p = null;
		boolean result = false;
		try {
			ByteBuffer mapped;
			p = Files.createTempFile("unlink-test", ".tmp");
			FileChannel chan = FileChannel.open(p, StandardOpenOption.READ, StandardOpenOption.WRITE);
			chan.write(ByteBuffer.allocate(4*1024));
			mapped = chan.map(MapMode.READ_WRITE, 0, 4*1024);
			chan.close();
			Files.delete(p);
			result = mapped.get() == 0;
		} catch (IOException e) {
			e.printStackTrace();
			if(p != null) {
				Path toDelete = p;
				Runtime.getRuntime().addShutdownHook(new Thread(() -> {
					try {
						Files.deleteIfExists(toDelete);
					} catch (IOException e1) {
						e1.printStackTrace();
					}
				}));

			}
		}

		MAP_AND_UNLINK_SUPPORTED = result;
	}
	
	/**
	 * on posix systems: allocates disk-backed bytebuffer and immediately unlinks the file
	 * on others: simply returns a direct bytebuffer
	 */
	public static ByteBuffer allocate(int size) {
		if(MAP_AND_UNLINK_SUPPORTED) {
			try {
				Path p = Files.createTempFile("anon-mapping", ".tmp");
				ByteBuffer mapped;
				FileChannel chan = FileChannel.open(p, StandardOpenOption.READ, StandardOpenOption.WRITE);
				chan.position(size);
				chan.write(ByteBuffer.allocate(1));
				mapped = chan.map(MapMode.READ_WRITE, 0, size);
				chan.close();
				Files.delete(p);
				return mapped;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		return ByteBuffer.allocateDirect(size);
	}

}
