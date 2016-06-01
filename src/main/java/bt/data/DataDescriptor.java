package bt.data;

import java.util.List;

public class DataDescriptor implements IDataDescriptor {

    private List<IChunkDescriptor> chunkDescriptors;

    public DataDescriptor(List<IChunkDescriptor> chunkDescriptors) {
        this.chunkDescriptors = chunkDescriptors;
    }

    public List<IChunkDescriptor> getChunkDescriptors() {
        return chunkDescriptors;
    }

    @Override
    public String toString() {

        // TODO: remove me or move to a different method
        StringBuilder buf = new StringBuilder();
        int i = 0;
        for (IChunkDescriptor chunk : chunkDescriptors) {
            buf.append("Chunk #" + ++i + ": " + chunk.toString() + "\n");
        }
        return buf.toString();
    }
}
