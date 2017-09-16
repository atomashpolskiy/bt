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

import bt.bencoding.model.rule.Rule;

import java.util.List;

abstract class BaseBEObjectModel implements BEObjectModel {

    private List<Rule> rules;

    BaseBEObjectModel(List<Rule> rules) {
        this.rules = rules;
    }

    @Override
    public final ValidationResult validate(Object object) {

        ValidationResult result;
        if (object != null) {

            // unwrap BEObjects
            if (object instanceof BEObject) {
                object = ((BEObject) object).getValue();
            }

            Class<?> javaType = TypesMapping.getJavaTypeForBEType(getType());
            if (!javaType.isAssignableFrom(object.getClass())) {
                result = new ValidationResult();
                result.addMessage("Wrong type -- expected " + javaType.getName()
                        + ", actual: " + object.getClass().getName());
                return result;
            }
        }

        result = validateObject(object);
        // fail-fast if any rules failed
        return result.isSuccess()? afterValidate(result, object) : result;
    }

    private ValidationResult validateObject(Object object) {
        ValidationResult result = new ValidationResult();
        rules.stream()
                .filter(rule -> !rule.validate(object))
                .map(Rule::getDescription)
                .forEach(result::addMessage);
        return result;
    }

    /**
     * Contribute to the validation result.
     *
     * @since 1.0
     */
    protected abstract ValidationResult afterValidate(ValidationResult validationResult, Object object);
}
