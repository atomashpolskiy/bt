/*
 * Copyright (c) 2016—2021 Andrei Tomashpolskiy and individual contributors.
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

/**
 * An enum which specifies the priority of a file for downloading.
 *
 * @since 1.10
 */
public enum FilePriority {
    /**
     * Skip this file.
     *
     * @since 1.10
     */
    SKIP,
    /**
     * Download this file with normal priority.
     *
     * @since 1.10
     */
    NORMAL_PRIORITY,
    /**
     * Download this file with high priority.
     *
     * @since 1.10
     */
    HIGH_PRIORITY
}
