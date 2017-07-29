/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.mldht.cli.commands;

import static the8472.utils.Functional.awaitAll;

import the8472.bt.TorrentUtils;
import the8472.mldht.TorrentFetcher;
import the8472.mldht.TorrentFetcher.FetchState;
import the8472.mldht.TorrentFetcher.FetchTask;
import the8472.mldht.cli.CommandProcessor;

import lbms.plugins.mldht.kad.Key;
import lbms.plugins.mldht.utils.NIOConnectionManager;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.stream.Collectors;

public class GetTorrent extends CommandProcessor {
	
	NIOConnectionManager conMan;
	
	ScheduledThreadPoolExecutor timer;

	@Override
	protected void process() {
		TorrentFetcher fetcher = new TorrentFetcher(dhts);
		
		List<CompletionStage<FetchTask>> tasks = arguments.stream().filter(Key.STRING_PATTERN.asPredicate()).map(hash -> {
			Key targetKey = new Key(hash);
			
			FetchTask task = fetcher.fetch(targetKey);
			
			CompletionStage<FetchTask> st = task.awaitCompletion();
			return st.whenComplete(this::handleCompletion);
		}).collect(Collectors.toList());
		
		awaitAll(tasks).handle((results, ex) -> {
			if(ex != null) {
				handleException(ex);
				exit(1);
			} else {
				int success = (int) results.stream().filter(r -> r.getState() == FetchState.SUCCESS).count();
				println(success+ "/" +results.size()+" downloads successful");
				exit(0);
			}
			return null;
		});
		
		
		
	}
	
	void handleCompletion(FetchTask task, Throwable ex) {
		if(ex != null)
			return;
		if(task.getState() != FetchState.SUCCESS) {
			printErr("download "+task.infohash().toString(false)+" failed\n");
			return;
		}
			
		task.getResult().ifPresent(buf -> {
			Path torrentName = currentWorkDir.resolve(task.infohash().toString(false) +".torrent");
			try(ByteChannel chan = Files.newByteChannel(torrentName, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
				ByteBuffer torrent = TorrentUtils.wrapBareInfoDictionary(buf);
				
				Optional<String> name = TorrentUtils.getTorrentName(torrent);
				
				chan.write(torrent);
				name.ifPresent(str -> {
					println("torrent name: "+ str);
				});
				println("written meta to "+torrentName);
			} catch (IOException e) {
				handleException(e);
			}
		});
	}


}
