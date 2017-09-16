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

/**
 * Object model.
 *
 * @since 1.0
 */
public interface BEObjectModel {

    /**
     * @return BEncoding type of the objects that this model can be applied to.
     * @since 1.0
     */
    BEType getType();

    /**
     * Validate a given object.
     *
     * @param object Object of this model's BEncoding type.
     * @return Validation result (failed, if this model cannot be applied to the {@code object}'s type).
     * @since 1.0
     */
    ValidationResult validate(Object object);
}
