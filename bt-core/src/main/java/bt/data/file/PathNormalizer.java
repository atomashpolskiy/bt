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

package bt.data.file;

import java.nio.file.FileSystem;
import java.util.List;
import java.util.StringTokenizer;

class PathNormalizer {
    private final String separator;

    public PathNormalizer(FileSystem fileSystem) {
        separator =  fileSystem.getSeparator();
    }

    public String normalize(List<String> path) {
        if (path.isEmpty()) {
            return "_";
        } else if (path.size() == 1) {
            return normalize(path.get(0));
        } else {
            StringBuilder buf = new StringBuilder();
            path.forEach(element -> {
                buf.append(element);
                buf.append(separator);
            });
            buf.delete(buf.length() - separator.length(), buf.length());
            return normalize(buf.toString());
        }
    }

    public String normalize(String path) {
        String normalized = path.trim();
        if (normalized.isEmpty()) {
            return "_";
        }

        StringTokenizer tokenizer = new StringTokenizer(normalized, separator, true);
        StringBuilder buf = new StringBuilder(normalized.length());
        boolean first = true;
        while (tokenizer.hasMoreTokens()) {
            String element = tokenizer.nextToken();
            if (separator.equals(element)) {
                if (first) {
                    buf.append("_");
                }
                buf.append(separator);
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
        String normalized = pathElement.trim();
        if (normalized.isEmpty()) {
            return "_";
        }

        // truncate trailing whitespaces and dots;
        // this will also eliminate '.' and '..' relative names
        char[] value = normalized.toCharArray();
        int to = value.length;
        while (to > 0 && (value[to - 1] == '.' || value[to - 1] == ' ')) {
            to--;
        }
        if (to == 0) {
            normalized = "";
        } else if (to < value.length) {
            normalized = normalized.substring(0, to);
        }

        return normalized.isEmpty() ? "_" : normalized;
    }

    private String replaceTrailingSlashes(String path) {
        if (path.isEmpty()) {
            return path;
        }

        int k = 0;
        while (path.endsWith(separator)) {
            path = path.substring(0, path.length() - separator.length());
            k++;
        }
        if (k > 0) {
            char[] separatorChars = separator.toCharArray();
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
}
