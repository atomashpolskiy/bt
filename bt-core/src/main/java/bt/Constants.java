package bt;

public class Constants {

    public static final int INFO_HASH_LENGTH = 20;
    public static final int PEER_ID_LENGTH = 20;

    /**
     * Maximum allowed block size: 128KB
     */
    public static final int MAX_BLOCK_SIZE = 2 << 16;

    /**
     * Transfer block size in BT: 16KB
     */
    public static final int BLOCK_SIZE = 2 << 13;
}
