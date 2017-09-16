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
import java.util.Map;

class BEMapModel extends BaseBEObjectModel {

    private Map<String, BEObjectModel> entriesModel;

    BEMapModel(Map<String, BEObjectModel> entriesModel, List<Rule> rules) {
        super(rules);
        this.entriesModel = entriesModel;
    }

    @Override
    public BEType getType() {
        return BEType.MAP;
    }

    @Override
    protected ValidationResult afterValidate(ValidationResult validationResult, Object object) {

        if (object != null) {

            Map<?,?> map = (Map<?,?>) object;
            for (Map.Entry<String, BEObjectModel> entryModel : entriesModel.entrySet()) {

                String key = entryModel.getKey();
                entryModel.getValue().validate(map.get(key)).getMessages()
                        .forEach(validationResult::addMessage);
            }
        }

        return validationResult;
    }
}
