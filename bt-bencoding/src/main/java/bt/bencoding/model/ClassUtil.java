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

import java.util.List;
import java.util.Map;

/**
 * Provides utility functions for unchecked conversions.
 *
 * @since 1.0
 */
public class ClassUtil {

    /**
     * @since 1.3
     */
    @SuppressWarnings("rawtypes")
    public static <T> T read(Map map, Class<T> type, Object key) throws Exception {
        Object value = map.get(key);
        return (value == null) ? (T) value : cast(type, key, value);
    }

    /**
     * @since 1.0
     */
    @SuppressWarnings("rawtypes")
    public static <T> T readNotNull(Map map, Class<T> type, Object key) throws Exception {
        Object value = map.get(key);
        if (value == null) {
            throw new Exception("Value is missing for key: " + key);
        }
        return cast(type, key, value);
    }

    /**
     * @since 1.0
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static <T> T cast(Class<T> type, Object key, Object value) throws Exception {
        if (value == null) {
            return null;
        }
        if (!type.isAssignableFrom(value.getClass())) {
            throw new Exception("Value has invalid type" + (key == null? "" : " for key: " + key)
                    + " -- expected '" + type.getName() + "', got: " + value.getClass().getName());
        }
        return (T) value;
    }

    /**
     * @since 1.0
     */
    @SuppressWarnings("unchecked")
    public static <T> List<T> castList(Class<T> elementType, List<?> list) throws Exception {
        if (list == null) {
            return null;
        }
        for (Object element : list) {
            if (!elementType.isAssignableFrom(element.getClass())) {
                throw new Exception("List element has invalid type -- expected '"
                    + elementType.getName() + "', got: " + element.getClass().getName());
            }
        }
        return (List<T>) list;
    }
}
