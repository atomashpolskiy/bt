package bt.magnet;

import bt.protocol.extended.ExtendedMessage;

import java.util.Objects;
import java.util.Optional;

public class UtMetadata extends ExtendedMessage {
    public enum Type {
        REQUEST(0), DATA(1), REJECT(2);

        private final int id;

        Type(int id) {
            this.id = id;
        }

        int id() {
            return id;
        }

        static Type forId(int id) {
            for (Type type : values()) {
                if (type.id == id) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown message id: " + id);
        }
    }

    private static final String id = "ut_metadata";
    private static final String messageTypeField = "msg_type";
    private static final String pieceIndexField = "piece";
    private static final String totalSizeField = "total_size";

    static String id() {
        return id;
    }

    static String messageTypeField() {
        return messageTypeField;
    }

    static String pieceIndexField() {
        return pieceIndexField;
    }

    static String totalSizeField() {
        return totalSizeField;
    }

    public static UtMetadata request(int pieceIndex) {
        return new UtMetadata(Type.REQUEST, pieceIndex);
    }

    public static UtMetadata data(int pieceIndex, int totalSize, byte[] data) {
        return new UtMetadata(Type.DATA, pieceIndex, totalSize, Objects.requireNonNull(data));
    }

    public static UtMetadata reject(int pieceIndex) {
        return new UtMetadata(Type.REJECT, pieceIndex);
    }

    private final Type type;
    private final int pieceIndex;
    private final Optional<Integer> totalSize;
    private final Optional<byte[]> data;

    UtMetadata(Type type, int pieceIndex) {
        this(type, pieceIndex, null, null);
    }

    UtMetadata(Type type, int pieceIndex, Integer totalSize, byte[] data) {
        if (pieceIndex < 0) {
            throw new IllegalArgumentException("Invalid piece index: " + pieceIndex);
        }
        if (totalSize != null && totalSize <= 0) {
            throw new IllegalArgumentException("Invalid total size: " + totalSize);
        }
        this.type = type;
        this.pieceIndex = pieceIndex;
        this.totalSize = Optional.ofNullable(totalSize);
        this.data = Optional.ofNullable(data);
    }

    public Type getType() {
        return type;
    }

    public int getPieceIndex() {
        return pieceIndex;
    }

    public Optional<byte[]> getData() {
        return data;
    }

    public Optional<Integer> getTotalSize() {
        return totalSize;
    }

    @Override
    public String toString() {
        String s = "[" + this.getClass().getSimpleName() + "] type {" + type.name() + "}, piece index {" + pieceIndex + "}";
        if (type == Type.DATA) {
            s += ", data {" + data.get().length + " bytes}, total size {" + totalSize.get() + "}";
        }
        return s;
    }
}
