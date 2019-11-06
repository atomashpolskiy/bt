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

package bt.torrent.messaging;

import bt.data.Bitfield;
import bt.net.ConnectionKey;
import bt.runtime.Config;
import bt.torrent.BitfieldBasedStatistics;
import bt.torrent.selector.PieceSelector;

import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class Assignments {

    private Config config;

    private Bitfield bitfield;
    private PieceSelector selector;
    private BitfieldBasedStatistics pieceStatistics;

    private Set<Integer> assignedPieces;
    private Map<ConnectionKey, Assignment> assignments;

    public Assignments(Bitfield bitfield, PieceSelector selector, BitfieldBasedStatistics pieceStatistics, Config config) {
        this.bitfield = bitfield;
        this.selector = selector;
        this.pieceStatistics = pieceStatistics;
        this.config = config;

        this.assignedPieces = new HashSet<>();
        this.assignments = new HashMap<>();
    }

    public Assignment get(ConnectionKey connectionKey) {
        return assignments.get(connectionKey);
    }

    public void remove(Assignment assignment) {
        assignment.abort();
        assignments.remove(assignment.getConnectionKey());
        // TODO: investigate on how this might affect endgame?
        assignedPieces.removeAll(assignment.getPieces());
    }

    public int count() {
        return assignments.size();
    }

    public Optional<Assignment> assign(ConnectionKey connectionKey) {
        if (!hasInterestingPieces(connectionKey)) {
            return Optional.empty();
        }

        Assignment assignment = new Assignment(connectionKey, config.getMaxPieceReceivingTime(),
                selector, pieceStatistics, this);
        assignments.put(connectionKey, assignment);
        return Optional.of(assignment);
    }

    public boolean claim(int pieceIndex) {
        boolean claimed = !bitfield.isComplete(pieceIndex) && (isEndgame() ||  !assignedPieces.contains(pieceIndex));
        if (claimed) {
            assignedPieces.add(pieceIndex);
        }
        return claimed;
    }

    public void finish(Integer pieceIndex) {
        assignedPieces.remove(pieceIndex);
    }

    public boolean isEndgame() {
        // if all remaining pieces are requested,
        // that would mean that we have entered the "endgame" mode
        return bitfield.getPiecesRemaining() <= assignedPieces.size();
    }

    /**
     * @return Collection of peers that have interesting pieces and can be given an assignment
     */
    public Set<ConnectionKey> update(Set<ConnectionKey> ready, Set<ConnectionKey> choking) {
        Set<ConnectionKey> result = new HashSet<>();
        for (ConnectionKey peer : ready) {
            if (hasInterestingPieces(peer)) {
                result.add(peer);
            }
        }
        for (ConnectionKey peer : choking) {
            if (hasInterestingPieces(peer)) {
                result.add(peer);
            }
        }

        return result;
    }

    private boolean hasInterestingPieces(ConnectionKey connectionKey) {
        Optional<Bitfield> peerBitfieldOptional = pieceStatistics.getPeerBitfield(connectionKey);
        if (!peerBitfieldOptional.isPresent()) {
            return false;
        }
        BitSet peerBitfield = peerBitfieldOptional.get().getBitmask();
        BitSet localBitfield = bitfield.getBitmask();
        peerBitfield.andNot(localBitfield);
        return peerBitfield.cardinality() > 0;
    }
}
