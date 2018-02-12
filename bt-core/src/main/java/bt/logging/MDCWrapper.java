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

package bt.logging;

import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class MDCWrapper {
    public static final String REMOTE_ADDRESS = "remoteAddress";

    private final Map<String, Object> context;

    public MDCWrapper() {
        context = new HashMap<>();
    }

    public MDCWrapper putRemoteAddress(Object value) {
        return put(REMOTE_ADDRESS, value);
    }

    public MDCWrapper put(String key, Object value) {
        context.put(key, value);
        return this;
    }

    public <U> U supply(Supplier<U> supplier) {
        final Map<String, String> current = MDC.getCopyOfContextMap();
        try {
            updateMDC();
            return supplier.get();
        } finally {
            MDC.setContextMap(current);
        }
    }

    public void run(Runnable runnable) {
        final Map<String, String> current = MDC.getCopyOfContextMap();
        try {
            updateMDC();
            runnable.run();
        } finally {
            MDC.setContextMap(current);
        }
    }

    private void updateMDC() {
        context.forEach((key, value) -> {
            if (value == null) {
                MDC.remove(key);
            } else {
                MDC.put(key, String.valueOf(value));
            }
        });
    }
}
