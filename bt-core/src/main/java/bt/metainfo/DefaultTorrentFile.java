/*
 * Copyright (c) 2016—2017 Andrei Tomashpolskiy and individual contributors.
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

package bt.metainfo;

import bt.BtException;

import java.util.List;

class DefaultTorrentFile implements TorrentFile {

    private long size;
    private List<String> pathElements;

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public List<String> getPathElements() {
        return pathElements;
    }

    public void setSize(long size) {
        if (size < 0) {
            throw new BtException("Invalid torrent file size: " + size);
        }
        this.size = size;
    }

    public void setPathElements(List<String> pathElements) {
        if (pathElements == null || pathElements.isEmpty()) {
            throw new BtException("Can't create torrent file without path");
        }
        this.pathElements = pathElements;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        DefaultTorrentFile that = (DefaultTorrentFile) obj;
        return size == that.size && pathElements.equals(that.pathElements);

    }

    @Override
    public int hashCode() {
        int result = (int) (size ^ (size >>> 32));
        result = 31 * result + pathElements.hashCode();
        return result;
    }
}
