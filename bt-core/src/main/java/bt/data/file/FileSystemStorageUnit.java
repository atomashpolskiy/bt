package bt.data.file;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bt.BtException;
import bt.data.StorageUnit;

class FileSystemStorageUnit implements StorageUnit {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemStorageUnit.class);

    private Path parent, file;
    private SeekableByteChannel sbc;
    private long capacity;

    private volatile boolean closed;

    FileSystemStorageUnit(Path root, String path, long capacity) {
    	this.file = root.resolve(path);
    	this.parent = file.getParent();
        this.capacity = capacity;
        this.closed = true;
    }

    // TODO: this is temporary fix for verification upon app start
    // should be re-done (probably need additional API to know if storage unit is "empty")
    private boolean init(boolean create) {

        if (closed) {
        	if (!Files.exists(parent)) {
        		try {
        			Files.createDirectory(parent);
        		} catch(IOException e) {
        			if(create) {
        				throw new BtException("Failed to create file storage -- can't create (some of the) directories");
        			}
        			throw new BtException("Failed to create file storage -- unexpected I/O error", e);
        		}
            }
        	
            if (!Files.exists(file)) {
                if (create) {
                    try {
                		Files.createFile(file);
                    } catch (IOException e) {
                    	throw new BtException("Failed to create file storage -- " +
                                "can't create new file: " + file.toAbsolutePath());
                    }
                } else {
                    return false;
                }
            }

            try {
            	sbc = Files.newByteChannel(file, StandardOpenOption.READ, StandardOpenOption.WRITE);
            } catch (IOException e) {
            	throw new BtException("Unexpected I/O error", e);
			}

            closed = false;
        }
        return true;
    }

    @Override
    public synchronized void readBlock(ByteBuffer buffer, long offset) {

        if (closed) {
            if (!init(false)) {
                return;
            }
        }

        if (offset < 0) {
            throw new BtException("Illegal arguments: offset (" + offset + ")");
        } else if (offset > capacity - buffer.remaining()) {
            throw new BtException("Received a request to read past the end of file (offset: " + offset +
                    ", requested block length: " + buffer.remaining() + ", file size: " + capacity);
        }

        try {
        	sbc.position(offset);
        	sbc.read(buffer);
    	
        } catch (IOException e) {
            throw new BtException("Failed to read bytes (offset: " + offset +
                    ", requested block length: " + buffer.remaining() + ", file size: " + capacity + ")", e);
        }
    }

    @Override
    public synchronized byte[] readBlock(long offset, int length) {

        if (closed) {
            if (!init(false)) {
                // TODO: should we return null here? or init this "stub" in constructor?
                return new byte[length];
            }
        }

        if (offset < 0 || length < 0) {
            throw new BtException("Illegal arguments: offset (" + offset + "), length (" + length + ")");
        } else if (offset > capacity - length) {
            throw new BtException("Received a request to read past the end of file (offset: " + offset +
                    ", requested block length: " + length + ", file size: " + capacity);
        }

        try {
        	sbc.position(offset);
            byte[] block = new byte[length];
            sbc.read(ByteBuffer.wrap(block));
            return block;

        } catch (IOException e) {
            throw new BtException("Failed to read bytes (offset: " + offset +
                    ", requested block length: " + length + ", file size: " + capacity + ")", e);
        }
    }

    @Override
    public synchronized void writeBlock(ByteBuffer buffer, long offset) {

        if (closed) {
            init(true);
        }

        if (offset < 0) {
            throw new BtException("Negative offset: " + offset);
        } else if (offset > capacity - buffer.remaining()) {
            throw new BtException("Received a request to write past the end of file (offset: " + offset +
                    ", block length: " + buffer.remaining() + ", file size: " + capacity);
        }

        try {
        	sbc.position(offset);
        	sbc.write(buffer);

        } catch (IOException e) {
            throw new BtException("Failed to write bytes (offset: " + offset +
                    ", block length: " + buffer.remaining() + ", file size: " + capacity + ")", e);
        }
    }

    @Override
    public synchronized void writeBlock(byte[] block, long offset) {

        if (closed) {
            init(true);
        }

        if (offset < 0) {
            throw new BtException("Negative offset: " + offset);
        } else if (offset > capacity - block.length) {
            throw new BtException("Received a request to write past the end of file (offset: " + offset +
                    ", block length: " + block.length + ", file size: " + capacity);
        }

        try {
        	sbc.position(offset);
        	sbc.write(ByteBuffer.wrap(block));

        } catch (IOException e) {
            throw new BtException("Failed to write bytes (offset: " + offset +
                    ", block length: " + block.length + ", file size: " + capacity + ")", e);
        }
    }

    @Override
    public long capacity() {
        return capacity;
    }

    @Override
    public long size() {
    	try {
			return Files.size(file);
		} catch (IOException e) {
			throw new BtException("Unexpected I/O error", e);
		}
    }

    @Override
    public String toString() {
        return "(" + capacity + " B) " + file.getPath();
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            try {
                raf.close();
            } catch (IOException e) {
                LOGGER.warn("Failed to close file: " + file.getPath(), e);
            } finally {
                closed = true;
            }
        }
    }
}
