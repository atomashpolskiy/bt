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

package bt.torrent.selector;

import bt.data.Bitfield;

import java.util.function.IntPredicate;

public class IncompletePiecesValidator implements IntPredicate {

    private final Bitfield bitfield;

    public IncompletePiecesValidator(Bitfield bitfield) {
        this.bitfield = bitfield;
    }

    @Override
    public boolean test(int pieceIndex) {
        return !isComplete(pieceIndex);
    }

    private boolean isComplete(int pieceIndex) {
        return bitfield.isComplete(pieceIndex);
    }
}
