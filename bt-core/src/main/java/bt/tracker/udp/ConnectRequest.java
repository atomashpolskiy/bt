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

package bt.tracker.udp;

import java.io.IOException;
import java.io.OutputStream;

class ConnectRequest extends UdpTrackerMessage {

    private static final int CONNECT_TYPE_ID = 0;

    public ConnectRequest() {
        super(CONNECT_TYPE_ID);
    }

    @Override
    protected void writeBodyTo(OutputStream out) throws IOException {
        // do nothing
    }
}
