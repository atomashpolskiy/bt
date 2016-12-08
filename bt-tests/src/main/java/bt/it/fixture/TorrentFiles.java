package bt.it.fixture;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

class TorrentFiles {

    private Map<String, byte[]> files;

    TorrentFiles(Map<String, byte[]> files) {
        this.files = files;
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
}
