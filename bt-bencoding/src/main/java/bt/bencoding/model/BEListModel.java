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

import bt.bencoding.BEType;
import bt.bencoding.model.rule.Rule;

import java.util.List;

class BEListModel extends BaseBEObjectModel {

    private BEObjectModel elementModel;

    BEListModel(BEObjectModel elementModel, List<Rule> rules) {
        super(rules);
        this.elementModel = elementModel;
    }

    @Override
    public BEType getType() {
        return BEType.LIST;
    }

    @Override
    protected ValidationResult afterValidate(ValidationResult validationResult, Object object) {

        if (object != null) {
            List<?> list = (List<?>) object;
            for (Object element : list) {
                elementModel.validate(element).getMessages().forEach(validationResult::addMessage);
            }
        }

        return validationResult;
    }
}
