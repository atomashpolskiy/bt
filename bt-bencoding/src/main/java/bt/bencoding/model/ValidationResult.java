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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @since 1.0
 */
public class ValidationResult {

    private boolean success;
    private List<String> messages;

    ValidationResult() {
        success = true;
    }

    /**
     * @return true if validation is successful
     * @since 1.0
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * @return List of validation errors
     * @since 1.0
     */
    public List<String> getMessages() {
        return messages == null? Collections.emptyList() : Collections.unmodifiableList(messages);
    }

    void addMessage(String message) {
        if (messages == null) {
            messages = new ArrayList<>();
        }
        messages.add(message);
        success = false;
    }
}
