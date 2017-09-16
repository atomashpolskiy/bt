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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @since 1.2
 */
class ReadWriteDataRange implements DataRange {

    private final List<StorageUnit> units;
    private final int firstUnit;
    private final int lastUnit;
    private final long offsetInFirstUnit;
    private final long limitInLastUnit;

    private final long length;

    /**
     * This is a "map" of this range's "virtual addresses" (offsets) into the range's files.
     * Size of this map is equal to the number of range's files.
     * The number x at some position n is the "virtual" offset that designates the beginning
     * of the n-th file. So, a request to build a range beginning with offset x,
     * will translate to offset 0 in the n-th file (i.e. the beginning of the file).
     *
     * Obviously, the number at position 0 is always 0
     * and translates into the offset in the first file in the range (this offset is set upon creation of the range).
     *
     * Also, it's guaranted that the address at position n+1 is always greater than address at position n.
     */
    private long[] fileOffsets;

    /**
     * Create a data range.
     *
     * @param units List of storage units, the data from which should be accessible via this data range.
     * @param offsetInFirstUnit Offset from the beginning of the first unit in {@code units}; inclusive
     * @param limitInLastUnit Offset from the beginning of the last unit in {@code units}; exclusive
     *
     * @since 1.2
     */
    public ReadWriteDataRange(List<StorageUnit> units,
                              long offsetInFirstUnit,
                              long limitInLastUnit) {
        this(units,
             0,
             offsetInFirstUnit,
             units.size() - 1,
             limitInLastUnit);
    }

    private ReadWriteDataRange(List<StorageUnit> units,
                               int firstUnit,
                               long offsetInFirstUnit,
                               int lastUnit,
                               long limitInLastUnit) {

        if (units.isEmpty()) {
            throw new IllegalArgumentException("Empty list of units");
        }
        if (firstUnit < 0 || firstUnit > units.size() - 1) {
            throw new IllegalArgumentException("Invalid first unit index: " + firstUnit + ", expected 0.." + (units.size() - 1));
        }
        if (lastUnit < 0 || lastUnit > units.size() - 1) {
            throw new IllegalArgumentException("Invalid last unit index: " + lastUnit + ", expected 0.." + (units.size() - 1));
        }
        if (firstUnit > lastUnit) {
            throw new IllegalArgumentException("First unit index is greater than last unit index: " + firstUnit + " > " + lastUnit);
        }
        if (offsetInFirstUnit < 0 || offsetInFirstUnit > units.get(firstUnit).capacity() - 1) {
            throw new IllegalArgumentException("Invalid offset in first unit: " + offsetInFirstUnit +
                    ", expected 0.." + (units.get(firstUnit).capacity() - 1));
        }
        if (limitInLastUnit <= 0 || limitInLastUnit > units.get(lastUnit).capacity()) {
            throw new IllegalArgumentException("Invalid limit in last unit: " + limitInLastUnit +
                    ", expected 1.." + (units.get(lastUnit).capacity()));
        }
        if (firstUnit == lastUnit && offsetInFirstUnit >= limitInLastUnit) {
            throw new IllegalArgumentException("Offset is greater than limit in a single-unit range: " +
                    offsetInFirstUnit + " >= " + limitInLastUnit);
        }

        this.units = units;
        this.fileOffsets = calculateOffsets(units, offsetInFirstUnit);

        this.firstUnit = firstUnit;
        this.lastUnit = lastUnit;
        this.offsetInFirstUnit = offsetInFirstUnit;
        this.limitInLastUnit = limitInLastUnit;

        this.length = calculateLength(units, offsetInFirstUnit, limitInLastUnit);
    }

    private static long calculateLength(List<StorageUnit> units, long offsetInFirstUnit, long limitInLastUnit) {
        long size;
        if (units.size() == 1) {
            size = limitInLastUnit - offsetInFirstUnit;
        } else {
            size = units.get(0).capacity() - offsetInFirstUnit;
            for (int i = 1; i < units.size() - 1; i++) {
                size += units.get(i).capacity();
            }
            size += limitInLastUnit;
        }
        return size;
    }

    private static long[] calculateOffsets(List<StorageUnit> units, long offsetInFirstUnit) {
        long[] fileOffsets = new long[units.size()];
        // first "virtual" address (first file begins with offset 0)
        fileOffsets[0] = 0;
        if (units.size() > 1) {
            // it's possible that chunk does not have access to the entire first file
            fileOffsets[1] = units.get(0).capacity() - offsetInFirstUnit;
        }
        for (int i = 2; i < units.size(); i++) {
            fileOffsets[i] = fileOffsets[i - 1] + units.get(i - 1).capacity();
        }
        return fileOffsets;
    }

    @Override
    public long length() {
        return length;
    }

    @Override
    public ReadWriteDataRange getSubrange(long offset, long length) {
        if (length == 0) {
            throw new IllegalArgumentException("Requested empty subrange, expected length of 1.." + length());
        }
        if (offset < 0 || length < 0) {
            throw new IllegalArgumentException("Illegal arguments: offset (" + offset + "), length (" + length + ")");
        }
        if (offset >= length()) {
            throw new IllegalArgumentException("Offset is too large: " + offset + ", expected 0.." + (length() - 1));
        }
        if (offset == 0 && length == length()) {
            return this;
        }

        int firstRequestedFileIndex,
            lastRequestedFileIndex;

        long offsetInFirstRequestedFile,
             limitInLastRequestedFile;

        // determine the file that the requested block begins in
        firstRequestedFileIndex = -1;
        for (int i = 0; i < units.size(); i++) {
            if (offset < fileOffsets[i]) {
                firstRequestedFileIndex = i - 1;
                break;
            } else if (i == units.size() - 1) {
                // reached the last file
                firstRequestedFileIndex = i;
            }
        }

        offsetInFirstRequestedFile = offset - fileOffsets[firstRequestedFileIndex];
        if (firstRequestedFileIndex == 0) {
            // if the first requested file is the first file in chunk,
            // then we need to begin from this chunk's offset in that file
            // (in case this chunk has access only to a portion of the file)
            offsetInFirstRequestedFile += offsetInFirstUnit;
        }

        lastRequestedFileIndex = firstRequestedFileIndex;
        long remaining = length;
        do {
            // determine which files overlap with the requested block
            if (firstRequestedFileIndex == lastRequestedFileIndex) {
                remaining -= (units.get(lastRequestedFileIndex).capacity() - offsetInFirstRequestedFile);
            } else {
                remaining -= units.get(lastRequestedFileIndex).capacity();
            }
        } while (remaining > 0 && ++lastRequestedFileIndex < units.size());

        if (lastRequestedFileIndex >= units.size()) {
            // data in this chunk is insufficient to fulfill the block request
            throw new IllegalArgumentException("Insufficient data (offset: " + offset + ", requested length: " + length + ")");
        }
        // if remaining is negative now, then we need to
        // strip off some data from the last file
        limitInLastRequestedFile = units.get(lastRequestedFileIndex).capacity() + remaining;

        if (lastRequestedFileIndex == units.size() - 1) {
            if (limitInLastRequestedFile > limitInLastUnit) {
                // data in this chunk is insufficient to fulfill the block request
                throw new IllegalArgumentException("Insufficient data (offset: " + offset + ", requested length: " + length + ")");
            }
        }

        List<StorageUnit> _units = new ArrayList<>();
        int len = lastRequestedFileIndex - firstRequestedFileIndex;
        _units.addAll(Arrays.asList(Arrays.copyOfRange(units.toArray(
                new StorageUnit[len]), firstRequestedFileIndex, lastRequestedFileIndex + 1)));

        return new ReadWriteDataRange(
                _units,
                offsetInFirstRequestedFile,
                limitInLastRequestedFile);
    }

    @Override
    public DataRange getSubrange(long offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("Illegal arguments: offset (" + offset + ")");
        }
        return offset == 0 ? this : getSubrange(offset, length() - offset);
    }

    @Override
    public byte[] getBytes() {
        if (length() > Integer.MAX_VALUE) {
            throw new IllegalStateException("Range is too big: " + length());
        }

        byte[] block = new byte[(int) length()];
        ByteBuffer buffer = ByteBuffer.wrap(block);

        visitUnits(new DataRangeVisitor() {
            int offsetInBlock = 0;

            @Override
            public boolean visitUnit(StorageUnit unit, long off, long lim) {
                long len = lim - off;
                if (len > Integer.MAX_VALUE) {
                    throw new IllegalStateException("Too much data requested");
                }

                if (((long) offsetInBlock) + len > Integer.MAX_VALUE) {
                    // overflow -- isn't supposed to happen unless the algorithm in range is incorrect
                    throw new IllegalStateException("Integer overflow while constructing block");
                }

                buffer.limit(offsetInBlock + (int) len);
                buffer.position(offsetInBlock);
                unit.readBlock(buffer, off);
                offsetInBlock += len;

                return true;
            }
        });

        return block;
    }

    @Override
    public void putBytes(byte[] block) {
        if (block.length == 0) {
            return;
        } else if (block.length > length()) {
            throw new IllegalArgumentException(String.format(
                    "Data does not fit in this range (expected max %d bytes, actual: %d)", length(), block.length));
        }

        ByteBuffer buffer = ByteBuffer.wrap(block).asReadOnlyBuffer();
        visitUnits(new DataRangeVisitor() {

            int offsetInBlock = 0;
            int limitInBlock;

            @Override
            public boolean visitUnit(StorageUnit unit, long off, long lim) {
                long fileSize = lim - off;
                if (fileSize > Integer.MAX_VALUE) {
                    throw new IllegalStateException("Unexpected file size -- insufficient data in block");
                }

                limitInBlock = Math.min(buffer.capacity(), offsetInBlock + (int) fileSize);
                buffer.limit(limitInBlock);
                buffer.position(offsetInBlock);
                unit.writeBlock(buffer, off);
                offsetInBlock = limitInBlock;

                return offsetInBlock < block.length - 1;
            }
        });
    }

    @Override
    public void visitUnits(DataRangeVisitor visitor) {
        long off, lim;

        for (int i = firstUnit; i <= lastUnit; i++) {
            StorageUnit file = units.get(i);
            off = (i == firstUnit) ? offsetInFirstUnit : 0;
            lim = (i == lastUnit) ? limitInLastUnit : file.capacity();

            visitor.visitUnit(file, off, lim);
        }
    }
}
