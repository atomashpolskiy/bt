/*
 * Copyright (c) 2016—2018 Andrei Tomashpolskiy and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package bt.torrent.fileselector;

import bt.metainfo.TorrentFile;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

// note the limitation that empty files will always be created (even if they weren't selected)
public abstract class TorrentFileSelector {

    public Set<TorrentFile> selectFiles(List<TorrentFile> files) {
        Set<TorrentFile> selected = new HashSet<>();
        for (TorrentFile file : files) {
            if (!select(file).shouldSkip()) {
                selected.add(file);
            }
        }
        return selected;
    }

    protected abstract SelectionResult select(TorrentFile file);
}
