/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.utils;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;



public class FilesystemNotifications {
	
	WatchService service;
	
	Thread t = new Thread(this::run, "FS-notify");
	
	Map<Path, BiConsumer<Path, WatchEvent.Kind<?>>> callbacks = new HashMap<>();
	
	
	public FilesystemNotifications() {
		try {
			service = FileSystems.getDefault().newWatchService();
		} catch (IOException e) {
			throw new Error("should not happen");
		}
		t.setDaemon(true);
		t.start();
	}



	private void run() {
		try {
			while(true) {

				WatchKey k = service.take();
				Path dir = (Path) k.watchable();
				k.pollEvents().forEach(e -> {
					Path relative = (Path) e.context();
					Path absolute = dir.resolve(relative);
					BiConsumer<Path, WatchEvent.Kind<?>> callback = callbacks.get(absolute);
					if(callback != null)
						callback.accept(absolute, e.kind());
				});
				k.reset();

			}
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}


	
	public void addRegistration(Path p, BiConsumer<Path, WatchEvent.Kind<?>> callback) {
		Path toWatch = p;
		if(!Files.isDirectory(toWatch))
			toWatch = toWatch.getParent();
		
		try {
			WatchKey k = toWatch.register(service, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		callbacks.put(p, callback);
		
	}

}
