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

import java.util.List;

/**
 * Utils for building tracker queries
 */
public class QueryBuilderUtil {
    /**
     * Build a url query for a torrent tracker from the named values
     *
     * @param binaryQueryNamedValues the named values to build the url query for
     * @return the built url query
     */
    public static String buildQueryUrl(List<BinaryQueryNamedValue> binaryQueryNamedValues) {
        StringBuilder query = new StringBuilder();
        for (BinaryQueryNamedValue binaryQueryNamedValue : binaryQueryNamedValues) {
            if (query.length() > 0) {
                query.append('&');
            }

            query.append(binaryQueryNamedValue.getName());
            query.append('=');
            query.append(binaryQueryNamedValue.getUrlEncodedValue());
        }
        return query.toString();
    }
}
