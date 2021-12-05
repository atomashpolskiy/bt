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

package bt.data.digest;

import java.lang.ref.SoftReference;
import java.util.function.Function;
import java.util.function.Supplier;

public class SoftThreadLocal<T> extends ThreadLocal<SoftReference<T>> {

    protected final Supplier<T> supplier;
    protected final Function<? super T, ? extends T> onGet;

    public SoftThreadLocal(Supplier<T> supplier) {
        this(supplier, null);
    }

    public SoftThreadLocal(Supplier<T> supplier, Function<? super T, ? extends T> onGet) {
        this.supplier = supplier;
        this.onGet = onGet;
    }

    protected T init() {
        return supplier.get();
    }

    public T getValue() {
        SoftReference<T> reference = get();
        T t = reference.get();
        if (t == null) {
            t = init();
            setValue(t);
        }
        if (onGet != null)
            t = onGet.apply(t);

        return t;
    }

    public final void setValue(T t) {
        set(new SoftReference<T>(t));
    }

    @Override
    protected final SoftReference<T> initialValue() {
        return new SoftReference<T>(init());
    }
}