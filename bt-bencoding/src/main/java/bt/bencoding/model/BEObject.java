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

package bt.bencoding.model;

import bt.bencoding.BEType;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Generic bencoded object.
 *
 * @param <T> Java type of the object's value.
 * @since 1.0
 */
public interface BEObject<T> {

    /**
     * @return Object's type
     * @since 1.0
     */
    BEType getType();

    /**
     * @return Binary representation of this object, as read from source
     *         (excluding type prefix and terminator, if applicable).
     * @since 1.0
     */
    byte[] getContent();

    /**
     * @return Object's value
     * @since 1.0
     */
    T getValue();

    /**
     * Write this object's contents to the provided stream (excluding type prefix and terminator).
     *
     * @since 1.0
     */
    void writeTo(OutputStream out) throws IOException;
}
