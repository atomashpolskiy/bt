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

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CookieTest {

    @Test
    public void testCookie_IdempotentSerializeDeserialize() throws Exception {
        Cookie cookie = Cookie.newCookie();
        assertTrue(Cookie.sameValue(cookie, Cookie.fromString(cookie.toString())));
    }

    @Test
    public void testCookie_UnknownCookie() {
        assertFalse(Cookie.sameValue(Cookie.unknownCookie(), Cookie.unknownCookie()));
    }

    @Test
    public void testCookie_SameValues() throws Exception {
        String value = "7fffffff";
        assertTrue(Cookie.sameValue(Cookie.fromString(value), Cookie.fromString(value)));
    }

    @Test(expected = NumberFormatException.class)
    public void testCookie_UnknownFormat() throws Exception {
        Cookie.fromString("7fffffff00000001");
    }
}
