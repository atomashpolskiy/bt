package bt.data.file;

import java.io.File;
import java.util.List;
import java.util.StringTokenizer;

class PathNormalizer {

    public String normalize(List<String> path) {
        if (path.isEmpty()) {
            return "_";
        } else if (path.size() == 1) {
            return normalize(path.get(0));
        } else {
            StringBuilder buf = new StringBuilder();
            path.forEach(element -> {
                buf.append(element);
                buf.append(File.separator);
            });
            buf.delete(buf.length() - File.separator.length(), buf.length());
            return normalize(buf.toString());
        }
    }

    public String normalize(String path) {
        String normalized = path.trim();
        if (normalized.isEmpty()) {
            return "_";
        }

        StringTokenizer tokenizer = new StringTokenizer(normalized, File.separator, true);
        StringBuilder buf = new StringBuilder(normalized.length());
        boolean first = true;
        while (tokenizer.hasMoreTokens()) {
            String element = tokenizer.nextToken();
            if (File.separator.equals(element)) {
                if (first) {
                    buf.append("_");
                }
                buf.append(File.separator);
                // this will handle inner slash sequences, like ...a//b...
                first = true;
            } else {
                buf.append(normalizePathElement(element));
                first = false;
            }
        }

        normalized = buf.toString();
        return replaceTrailingSlashes(normalized);
    }

    private String normalizePathElement(String pathElement) {
        // truncate leading and trailing whitespaces
        String normalized = trim(pathElement, ' ', '.');
        if (normalized.isEmpty()) {
            return "_";
        }

        // truncate trailing dots;
        // this will also eliminate '.' and '..' relative names
        char[] value = normalized.toCharArray();
        int to = value.length - 1;
        while (to >= 0 && value[to] == '.') {
            to--;
        }
        if (to == -1) {
            normalized = "";
        } else if (to < value.length - 1) {
            normalized = normalized.substring(0, to);
        }

        return normalized.isEmpty() ? "_" : normalized;
    }

    private String replaceTrailingSlashes(String path) {
        if (path.isEmpty()) {
            return path;
        }

        int k = 0;
        while (path.endsWith(File.separator)) {
            path = path.substring(0, path.length() - File.separator.length());
            k++;
        }
        if (k > 0) {
            char[] separatorChars = File.separator.toCharArray();
            char[] value = new char[path.length() + (separatorChars.length + 1) * k];
            System.arraycopy(path.toCharArray(), 0, value, 0, path.length());
            for (int offset = path.length(); offset < value.length; offset += separatorChars.length + 1) {
                System.arraycopy(separatorChars, 0, value, offset, separatorChars.length);
                value[offset + separatorChars.length] = '_';
            }
            path = new String(value);
        }

        return path;
    }

    private String trim(String s, char... chars) {
        char[] value = s.toCharArray();
        int len = value.length;
        int st = 0;

        while ((st < len) && isOneOf(value[st], chars)) {
            st++;
        }
        while ((st < len) && isOneOf(value[st], chars)) {
            len--;
        }
        return ((st > 0) || (len < value.length)) ? s.substring(st, len) : s;
    }

    private static boolean isOneOf(char tested, char... chars) {
        for (char c : chars) {
            if (tested == c) {
                return true;
            }
        }
        return false;
    }
}
