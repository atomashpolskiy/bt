package bt.tracker.http.urlencoding;

import java.util.List;

/**
 * Utils for building tracker queries
 */
public class QueryBuilderUtil {
    /**
     * Build a url query for a torrent tracker from the named values
     *
     * @param binaryQueryNamedValues the named values to build the url query for
     * @return the built url query
     */
    public static String buildQueryUrl(List<BinaryQueryNamedValue> binaryQueryNamedValues) {
        StringBuilder query = new StringBuilder();
        for (BinaryQueryNamedValue binaryQueryNamedValue : binaryQueryNamedValues) {
            if (query.length() > 0) {
                query.append('&');
            }

            query.append(binaryQueryNamedValue.getName());
            query.append('=');
            query.append(binaryQueryNamedValue.getUrlEncodedValue());
        }
        return query.toString();
    }
}
