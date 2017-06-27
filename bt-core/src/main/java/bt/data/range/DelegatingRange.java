package bt.data.range;

/**
 * @since 1.3
 */
public interface DelegatingRange<T extends Range<T>> {

    /**
     * @since 1.3
     */
    T getDelegate();
}
