package bt.torrent;

import java.util.Comparator;

/**
 * First compares the least significant 32 bits of Long values,
 * and if they are equal compares the most significant 32 bits.
 */
class PackedIntComparator implements Comparator<Long> {

    @Override
    public int compare(Long o1, Long o2) {
        if (o1.intValue() > o2.intValue()) {
            return 1;
        } else if (o1.intValue() < o2.intValue()) {
            return -1;
        } else {
            return Long.compare(o1 >> 32, o2 >> 32);
        }
    }
}
