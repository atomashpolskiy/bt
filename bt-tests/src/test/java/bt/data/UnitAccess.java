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
