package bt.tracker.http.urlencoding;

import java.util.ArrayList;
import java.util.List;

/**
 * A builder class to make a tracker query request with the correct encoding
 */
public class TrackerQueryBuilder {
    private List<BinaryQueryNamedValue> namedValues = new ArrayList<>();

    /**
     * Add a named field with a string value
     *
     * @param name  the name
     * @param value the value
     */
    public void add(String name, String value) {
        namedValues.add(new BinaryQueryNamedValue(name, value));
    }

    /**
     * Add a named field with a binary byte value
     *
     * @param name  the name
     * @param value the value
     */
    public void add(String name, byte[] value) {
        namedValues.add(new BinaryQueryNamedValue(name, value));
    }

    /**
     * Add a named field with a long value
     *
     * @param name  the name
     * @param value the value
     */
    public void add(String name, long value) {
        namedValues.add(new BinaryQueryNamedValue(name, value));
    }

    /**
     * Build the query from all of the added values
     *
     * @return the query
     */
    public String toQuery() {
        try {
            return QueryBuilderUtil.buildQueryUrl(namedValues);
        } finally {
            this.namedValues = null;
        }
    }
}
