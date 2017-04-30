package bt.data;

import bt.BtException;

import java.util.List;

/**
 * Represents a range of binary data, abstracting the mapping of data onto the storage layer.
 * Real data may span over several storage units or reside completely inside a single storage unit.
 *
 * @since 1.2
 */
public class DataRange {

    private List<StorageUnit> units;
    private int firstUnit;
    private int lastUnit;
    private long offsetInFirstUnit;
    private long limitInLastUnit;

    private long length;

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
    public DataRange(List<StorageUnit> units,
              long offsetInFirstUnit,
              long limitInLastUnit) {

        this.units = units;
        this.firstUnit = 0;
        this.lastUnit = units.size() - 1;
        this.offsetInFirstUnit = offsetInFirstUnit;
        this.limitInLastUnit = limitInLastUnit;

        this.length = calculateLength(units, offsetInFirstUnit, limitInLastUnit);
        this.fileOffsets = calculateOffsets(units, offsetInFirstUnit);
    }

    private DataRange(List<StorageUnit> units,
                      long[] fileOffsets,
                      int firstUnit,
                      long offsetInFirstUnit,
                      int lastUnit,
                      long limitInLastUnit) {

        this.units = units;
        this.fileOffsets = fileOffsets;
        this.firstUnit = firstUnit;
        this.offsetInFirstUnit = offsetInFirstUnit;
        this.lastUnit = lastUnit;
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

    /**
     * @return Length of this data range in bytes
     *
     * @since 1.2
     */
    public long length() {
        return length;
    }

    /**
     * Traverse the storage units in this data range.
     *
     * @since 1.2
     */
    public void visitUnits(DataRangeVisitor visitor) {
        long off, lim;

        for (int i = firstUnit; i <= lastUnit; i++) {
            StorageUnit file = units.get(i);
            off = (i == firstUnit) ? offsetInFirstUnit : 0;
            lim = (i == lastUnit) ? limitInLastUnit : file.capacity();

            visitor.visitUnit(file, off, lim);
        }
    }

    /**
     * Build a subrange of this data range.
     *
     * @param offset Offset from the beginning of the original data range in bytes, inclusive
     * @param length Length of the new data range
     * @return Subrange of the original data range
     *
     * @since 1.2
     */
    public DataRange getSubrange(long offset, long length) {

        if (offset < 0 || length < 0) {
            throw new BtException("Illegal arguments: offset (" + offset + "), length (" + length + ")");
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
            throw new BtException("Insufficient data (offset: " + offset + ", requested block length: " + length + ")");
        }
        // if remaining is negative now, then we need to
        // strip off some data from the last file
        limitInLastRequestedFile = units.get(lastRequestedFileIndex).capacity() + remaining;

        if (lastRequestedFileIndex == units.size() - 1) {
            if (limitInLastRequestedFile > limitInLastUnit) {
                // data in this chunk is insufficient to fulfill the block request
                throw new BtException("Insufficient data (offset: " + offset + ", requested block length: " + length + ")");
            }
        }

        return new DataRange(
                units,
                fileOffsets,
                firstRequestedFileIndex,
                offsetInFirstRequestedFile,
                lastRequestedFileIndex,
                limitInLastRequestedFile);
    }
}
