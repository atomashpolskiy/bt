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