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

package bt.peer.lan;

import java.io.IOException;
import java.util.Random;

/**
 * Opaque value, allowing the sending client to filter out its own announces if it receives them via multicast loopback.
 *
 * @since 1.6
 */
public class Cookie {

    /**
     * @return New cookie
     * @since 1.6
     */
    public static Cookie newCookie() {
        int value = new Random().nextInt() & 0x7fffffff; // need positive value
        return new Cookie(value);
    }

    private final String value;

    private Cookie(Number value) {
        this.value = value.toString();
    }

    /**
     * Write itself to the provided appendable entity.
     *
     * @since 1.6
     */
    public void appendTo(Appendable appendable) {
        try {
            appendable.append(value);
        } catch (IOException e) {
            throw new RuntimeException("Unexpected error", e);
        }
    }
}
