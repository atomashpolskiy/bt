package bt.it.fixture;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.StringTokenizer;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public class TorrentFiles {

    public static Builder builder(URL metainfoUrl) {
        return new Builder(metainfoUrl);
    }

    private URL metainfoUrl;
    private Map<String, byte[]> files;

    private TorrentFiles(URL metainfoUrl, Map<String, byte[]> files) {
        this.metainfoUrl = metainfoUrl;
        this.files = files;
    }

    public URL getMetainfoUrl() {
        return metainfoUrl;
    }

    public void createFiles(File root) {
        createRoot(root);
        files.forEach((path, content) -> {
            File file = getFile.apply(root, path);
            writeContents.accept(file, content);
        });
    }

    public void createRoot(File root) {
        if ((root.exists() && !root.isDirectory()) || (!root.exists() && !root.mkdirs())) {
            throw new RuntimeException("Failed to create directory: " + root);
        }
    }

    public boolean verifyFiles(File root) {

        for (Map.Entry<String, byte[]> entry : files.entrySet()) {

            String path = entry.getKey();
            byte[] expectedContent = entry.getValue();

            File file = getFile.apply(root, path);
            if (!verifyContents.apply(file, expectedContent)) {
                return false;
            }
        }
        return true;
    }

    private BiFunction<File, String, File> getFile = (root, path) -> {
        StringTokenizer tokenizer = new StringTokenizer(path, File.separator);
        if (!tokenizer.hasMoreTokens()) {
            throw new RuntimeException("Empty path");
        }
        File file = root;
        while (tokenizer.hasMoreTokens()) {
            file = new File(file, tokenizer.nextToken());
        }
        return file;
    };

    private BiConsumer<File, byte[]> writeContents = (file, content) -> {
        try {
            if (!file.createNewFile()) {
                throw new RuntimeException("Failed to create file: " + file.getPath());
            }
            try (FileOutputStream fout = new FileOutputStream(file)) {
                fout.write(content);
                fout.flush();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create file: " + file.getPath(), e);
        }
    };

    private BiFunction<File, byte[], Boolean> verifyContents = (file, expectedContent) -> {
        if (!file.exists()) {
            return false;
        }
        byte[] actualContent = new byte[(int) file.length()];
        try (FileInputStream fin = new FileInputStream(file)) {
            fin.read(actualContent);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + file.getPath(), e);
        }
        return Arrays.equals(expectedContent, actualContent);
    };

    public static class Builder {

        private URL metainfoUrl;
        private Map<String, byte[]> files;

        private Builder(URL metainfoUrl) {
            this.metainfoUrl = Objects.requireNonNull(metainfoUrl);
            files = new HashMap<>();
        }

        public Builder file(String relativePath, byte[] content) {
            files.put(relativePath, content);
            return this;
        }

        public TorrentFiles build() {
            if (files.isEmpty()) {
                throw new RuntimeException("Can't build -- no files");
            }
            return new TorrentFiles(metainfoUrl, files);
        }
    }
}
