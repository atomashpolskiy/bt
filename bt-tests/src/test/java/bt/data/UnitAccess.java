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

package bt.data;

import java.util.Objects;

class UnitAccess {

    private StorageUnit unit;
    private long off;
    private long lim;

    public UnitAccess(StorageUnit unit, long off, long lim) {
        this.unit = Objects.requireNonNull(unit);
        this.off = off;
        this.lim = lim;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (object == null || getClass() != object.getClass()) {
            return false;
        }

        UnitAccess that = (UnitAccess) object;
        return off == that.off && lim == that.lim && unit.equals(that.unit);
    }

    @Override
    public int hashCode() {
        int result = unit != null ? unit.hashCode() : 0;
        result = 31 * result + (int) (off ^ (off >>> 32));
        result = 31 * result + (int) (lim ^ (lim >>> 32));
        return result;
    }
}
