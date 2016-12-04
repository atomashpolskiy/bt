package bt.bencoding.model;

import bt.bencoding.BEType;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

class TypesMapping {

    public static Class<?> getJavaTypeForBEType(BEType type) {

        switch (type) {
            case MAP: return Map.class;
            case LIST: return List.class;
            case INTEGER: return BigInteger.class;
            case STRING: return byte[].class;
            default: {
                throw new IllegalArgumentException("Unknown BE type: " + type);
            }
        }
    }
}
