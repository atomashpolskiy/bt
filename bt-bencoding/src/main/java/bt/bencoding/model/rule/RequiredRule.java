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

package bt.bencoding.model.rule;

import bt.bencoding.model.ClassUtil;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Used to mark some attribute of an object model as required.
 *
 * @since 1.0
 */
public class RequiredRule implements Rule {

    private List<String> requiredKeys;

    /**
     * @param requiredKeys List of required attributes (in the order in which attributes should be checked).
     * @since 1.0
     */
    public RequiredRule(List<String> requiredKeys) {
        this.requiredKeys = requiredKeys;
    }

    @Override
    public boolean validate(Object object) {

        Map map;
        try {
            map = ClassUtil.cast(Map.class, null, object);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected validation exception", e);
        }

        for (String requiredKey : requiredKeys) {
            if (!map.containsKey(requiredKey)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String getDescription() {
        return "properties are required: " + Arrays.toString(requiredKeys.toArray());
    }

    @Override
    public String toString() {
        return getDescription();
    }
}
