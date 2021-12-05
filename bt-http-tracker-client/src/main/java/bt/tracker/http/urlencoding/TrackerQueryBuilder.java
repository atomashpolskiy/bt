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

package bt.tracker.http.urlencoding;

import java.util.ArrayList;
import java.util.List;

/**
 * A builder class to make a tracker query request with the correct encoding
 */
public class TrackerQueryBuilder {
    private List<BinaryQueryNamedValue> namedValues = new ArrayList<>();

    /**
     * Add a named field with a string value
     *
     * @param name  the name
     * @param value the value
     */
    public void add(String name, String value) {
        namedValues.add(new BinaryQueryNamedValue(name, value));
    }

    /**
     * Add a named field with a binary byte value
     *
     * @param name  the name
     * @param value the value
     */
    public void add(String name, byte[] value) {
        namedValues.add(new BinaryQueryNamedValue(name, value));
    }

    /**
     * Add a named field with a long value
     *
     * @param name  the name
     * @param value the value
     */
    public void add(String name, long value) {
        namedValues.add(new BinaryQueryNamedValue(name, value));
    }

    /**
     * Build the query from all of the added values
     *
     * @return the query
     */
    public String toQuery() {
        try {
            return QueryBuilderUtil.buildQueryUrl(namedValues);
        } finally {
            this.namedValues = null;
        }
    }
}
