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

public class SelectionResult {

    public static Builder select() {
        return new Builder();
    }

    public static SelectionResult skip() {
        return new SelectionResult(true);
    }

    private final boolean skip;

    private SelectionResult(boolean skip) {
        this.skip = skip;
    }

    public boolean shouldSkip() {
        return skip;
    }

    // later we may add more options:
    // - priority
    // - nofify-on-completed
    // etc.

    public static class Builder {

        private Builder() {
        }

        public SelectionResult build() {
            return new SelectionResult(false);
        }
    }
}
