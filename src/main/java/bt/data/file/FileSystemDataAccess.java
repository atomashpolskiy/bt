package bt.data.file;

import bt.BtException;
import bt.data.DataAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;

class FileSystemDataAccess implements DataAccess {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemDataAccess.class);

    private File parent, file;
    private long size;

    private boolean initialized;

    FileSystemDataAccess(File root, List<String> pathElements, long size) {

        if (pathElements.isEmpty()) {
            throw new BtException("Can't create file access -- no path elements");
        }

        File parent = root;
        int len = pathElements.size();
        for (int i = 0; i < len - 1; i++) {
            parent = new File(parent, pathElements.get(i));
        }
        this.parent = parent;
        this.file = new File(parent, pathElements.get(len - 1));

        this.size = size;

        init();
    }

    private void init() {

        if (!initialized) {
            if (!(parent.exists() || parent.mkdirs())) {
                throw new BtException("Failed to create file access -- can't create (some of the) directories");
            }

            if (file.exists()) {
                LOGGER.warn("File already exists, will overwrite: " + file.getAbsolutePath());
            } else {
                try {
                    if (file.createNewFile()) {
                        // create random file for reading/writing
                    } else {
                        throw new BtException("Failed to create file access -- " +
                                "can't create new file: " + file.getAbsolutePath());
                    }
                } catch (IOException e) {
                    throw new BtException("Failed to create file access -- unexpected I/O error", e);
                }
            }

            initialized = true;
        }
    }

    @Override
    public byte[] readBlock(int offset, int length) {
        // TODO: Implement me
        return null;
    }

    @Override
    public void writeBlock(byte[] block, int offset) {
        // TODO: Implement me
    }

    @Override
    public long size() {
        return size;
    }

    @Override
    public String toString() {
        return "(" + size + " B) " + file.getPath();
    }
}
