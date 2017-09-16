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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Used to mark a subset of the model's attributes as mutually exclusive.
 *
 * <p>Exclusive attributes can also be marked as required via {@link RequiredRule}.
 * In such case exclusiveness is checked first.
 * The exclusiveness condition is satisfied when either one of the following applies:
 * <ul>
 *     <li>the object has only one of the mutually exclusive attributes</li>
 *     <li>the object has neither of the mutually exclusive attributes</li>
 * </ul>
 * In the latter case an additional check is performed whether any of the mutually exclusive attributes is also
 * marked as required.
 *
 * <p>E.g. if:
 * <ul>
 *     <li>a model M defines attributes A, B and C,</li>
 *     <li>and attributes {B, C} are mutually exclusive,</li>
 *     <li>and attributes {A, C} are required,</li>
 * </ul>
 * then only the following types of objects will be allowed by model M:
 * <ul>
 *     <li>having attributes A and C</li>
 *     <li>having attributes A and B</li>
 * </ul>
 * The following types of objects will be disallowed by model M:
 * <ul>
 *     <li>having only a single attribute (any of {A, B, C})</li>
 *     <li>having all attributes A, B and C</li>
 *     <li>having only attibutes B and C (failing by exclusiveness rule, because it's applied first)</li>
 * </ul>
 *
 * @since 1.0
 */
public class ExclusiveRule implements Rule {

    boolean shouldCheckRequired;
    private RequiredRule exclusiveRequired;
    private RequiredRule otherRequired;
    private Collection<Set<String>> exclusives;

    /**
     * @param exclusives Collection of sets of mutually exclusive attributes.
     * @param required List of required attributes (in the order in which attributes should be checked).
     * @since 1.0
     */
    public ExclusiveRule(Collection<Set<String>> exclusives, List<String> required) {

        this.shouldCheckRequired = true;

        List<String> allExclusives = exclusives.stream().flatMap(Collection::stream).collect(Collectors.toList());

        List<String> otherRequiredKeys = new ArrayList<>();
        otherRequiredKeys.addAll(required);
        otherRequiredKeys.removeAll(allExclusives);
        this.otherRequired = new RequiredRule(otherRequiredKeys);

        List<String> exclusiveRequiredKeys = new ArrayList<>();
        exclusiveRequiredKeys.addAll(required);
        exclusiveRequiredKeys.removeAll(otherRequiredKeys);
        this.exclusiveRequired = new RequiredRule(exclusiveRequiredKeys);

        this.exclusives = exclusives;
    }

    /**
     * @param exclusives Collection of sets of mutually exclusive attributes.
     * @since 1.0
     */
    public ExclusiveRule(Collection<Set<String>> exclusives) {
        this.exclusives = exclusives;
    }

    @Override
    public boolean validate(Object object) {
        try {
            Map map = ClassUtil.cast(Map.class, null, object);
            long count = exclusives.stream()
                    .map(exclusive -> {
                        List<Object> found = new ArrayList<>(exclusive.size() + 1);
                        exclusive.forEach(key -> {
                            Object obj = map.get(key);
                            if (obj != null) {
                                found.add(obj);
                            }
                        });
                        return found;
                    })
                    .filter(found -> !found.isEmpty())
                    .count();

            if (count > 1) {
                return false;
            } else if (shouldCheckRequired) {
                if (count == 0) {
                    return exclusiveRequired.validate(object) && otherRequired.validate(object);
                } else {
                    return otherRequired.validate(object);
                }
            } else {
                return true;
            }

        } catch (Exception e) {
            throw new RuntimeException("Unexpected validation exception", e);
        }
    }

    @Override
    public String getDescription() {
        // TODO: do not print brackets for singleton exclusive sets
        String description = "properties are mutually exclusive: " + Arrays.toString(exclusives.toArray());
        if (exclusiveRequired != null) {
            description += "; " + exclusiveRequired.toString();
        }
        if (otherRequired != null) {
            description += "; " + otherRequired.toString();
        }
        return description;
    }

    @Override
    public String toString() {
        return getDescription();
    }
}
