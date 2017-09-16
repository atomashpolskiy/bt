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

package bt.protocol;

/**
 * Generic exception, that indicates that a message is invalid.
 * Usually thrown by message constructors.
 *
 * @since 1.0
 */
public class InvalidMessageException extends RuntimeException {

    /**
     * @since 1.0
     */
    public InvalidMessageException(String message) {
        super(message);
    }
}
