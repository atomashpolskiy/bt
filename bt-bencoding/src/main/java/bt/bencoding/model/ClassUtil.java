package bt.bencoding.model;

import java.util.List;
import java.util.Map;

/**
 * Provides utility functions for unchecked conversions.
 *
 * @since 1.0
 */
public class ClassUtil {

    /**
     * @since 1.0
     */
    @SuppressWarnings("rawtypes")
    public static <T> T readNotNull(Map map, Class<T> type, Object key) throws Exception {
        Object value = map.get(key);
        if (value == null) {
            throw new Exception("Value is missing for key: " + key);
        }
        return cast(type, key, value);
    }

    /**
     * @since 1.0
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static <T> T cast(Class<T> type, Object key, Object value) throws Exception {
        if (value == null) {
            return null;
        }
        if (!type.isAssignableFrom(value.getClass())) {
            throw new Exception("Value has invalid type" + (key == null? "" : " for key: " + key)
                    + " -- expected '" + type.getName() + "', got: " + value.getClass().getName());
        }
        return (T) value;
    }

    /**
     * @since 1.0
     */
    @SuppressWarnings("unchecked")
    public static <T> List<T> castList(Class<T> elementType, List<?> list) throws Exception {
        if (list == null) {
            return null;
        }
        for (Object element : list) {
            if (!elementType.isAssignableFrom(element.getClass())) {
                throw new Exception("List element has invalid type -- expected '"
                    + elementType.getName() + "', got: " + element.getClass().getName());
            }
        }
        return (List<T>) list;
    }
}
