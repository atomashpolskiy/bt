package bt.protocol.extended;

import java.util.function.BiConsumer;

/**
 * Represents a set of mappings between message types
 * (both literal names and Java types) and their numeric IDs.
 *
 * Used in BEP-10: Extension Protocol.
 * Message type name is specified in a dictionary of supported message types
 * in the extended handshake. Numeric message type ID is specified
 * in the binary representation of a message.
 *
 * @since 1.0
 */
public interface ExtendedMessageTypeMapping {

    /**
     * @param typeId Numeric message type ID
     * @return Message type name
     * @since 1.0
     */
    String getTypeNameForId(Integer typeId);

    /**
     * @param typeName Message type name
     * @return Numeric message type ID
     * @since 1.0
     */
    Integer getIdForTypeName(String typeName);

    /**
     * @param type Message Java type
     * @return Message type name
     * @since 1.0
     */
    String getTypeNameForJavaType(Class<?> type);

    /**
     * Visitor interface for all mappings, contained in this set.
     *
     * @param visitor First parameter is message type name,
     *                second parameter is numeric message type ID.
     * @since 1.0
     */
    void visitMappings(BiConsumer<String, Integer> visitor);
}
