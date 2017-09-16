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

import java.util.function.Function;

/**
 * Builds an object model from a Java-type based definition.
 *
 * @param <T> Java type of the model's definition object.
 * @since 1.0
 */
public interface BEObjectModelBuilder<T> extends Function<Object, BEObjectModel> {

    @Override
    default BEObjectModel apply(Object o) {

        if (o == null) {
            throw new NullPointerException("Missing source object -- null");
        }
        if (!getSourceType().isAssignableFrom(o.getClass())) {
            throw new IllegalStateException("Unexpected source type: " + o.getClass().getName());
        }

        @SuppressWarnings("unchecked")
        T t = (T) o;
        return buildModel(t);
    }

    /**
     * @return Model source type
     * @since 1.0
     */
    Class<T> getSourceType();

    /**
     * Build an object model from the provided model definition.
     *
     * @param t Model definition
     * @return Object model
     * @since 1.0
     */
    BEObjectModel buildModel(T t);
}
