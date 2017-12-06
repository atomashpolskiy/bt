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

package bt.net.pipeline;

import bt.protocol.Message;

/**
 * Encapsulates the algorithm of encoding/decoding data, that is transfered between two peers.
 *
 * @since 1.6
 */
public interface ChannelPipeline {

    /**
     * @return Incoming message, if there is sufficient data to decode it, or null
     * @since 1.6
     */
    Message decode();

    /**
     * @param message Outgoing message to encode
     * @return true, if there is sufficient space to encode the message
     * @since 1.6
     */
    boolean encode(Message message);

    /**
     * Attach channel handler to this pipeline
     *
     * @since 1.6
     */
    ChannelHandlerContext bindHandler(ChannelHandler handler);
}
