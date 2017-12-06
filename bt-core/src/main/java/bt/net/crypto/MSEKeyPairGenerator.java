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

package bt.net.crypto;

import bt.net.BigIntegers;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;

class MSEKeyPairGenerator {

    // 768-bit prime
    private static final BigInteger P = new BigInteger(
                                    "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD1" +
                                    "29024E088A67CC74020BBEA63B139B22514A08798E3404DD" +
                                    "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245" +
                                    "E485B576625E7EC6F44C42E9A63A36210000000000090563", 16);

    private static final BigInteger G = BigInteger.valueOf(2);

    static final int PUBLIC_KEY_BYTES = 96;

    private final SecureRandom random;
    private final int privateKeySize;

    MSEKeyPairGenerator(int privateKeySize) {
        if (privateKeySize < 16 || privateKeySize > 512) {
            throw new IllegalArgumentException("Illegal key size: " + privateKeySize + "; expected 16..512 bytes");
        }
        this.random = new SecureRandom();
        this.privateKeySize = privateKeySize;
    }

    KeyPair generateKeyPair() {
        MSEPrivateKey privateKey = new MSEPrivateKey(privateKeySize, random);
        MSEPublicKey publicKey = privateKey.getPublicKey();
        return new KeyPair(publicKey, privateKey);
    }

    int getPublicKeySize() {
        return PUBLIC_KEY_BYTES;
    }

    BigInteger calculateSharedSecret(BigInteger publicKey, PrivateKey privateKey) {
        if (privateKey instanceof MSEPrivateKey) {
            return ((MSEPrivateKey) privateKey).calculateSharedSecret(new MSEPublicKey(publicKey));
        }
        throw new IllegalArgumentException("Unsupported key type: " + privateKey.getClass().getName());
    }

    private static class MSEPublicKey implements PublicKey {

        private final BigInteger value;
        private volatile byte[] encoded;
        private final Object lock;

        private MSEPublicKey(BigInteger value) {
            this.value = value;
            this.lock = new Object();
        }

        BigInteger getValue() {
            return value;
        }

        @Override
        public String getAlgorithm() {
            return "DH";
        }

        @Override
        public String getFormat() {
            return "MSE"; // does it qualify for a format?
        }

        @Override
        public byte[] getEncoded() {
            if (encoded == null) {
                synchronized (lock) {
                    if (encoded == null) {
                        encoded = BigIntegers.encodeUnsigned(value, PUBLIC_KEY_BYTES);
                    }
                }
            }
            return encoded;
        }
    }

    private static class MSEPrivateKey implements PrivateKey {

        private final BigInteger value;
        private volatile MSEPublicKey publicKey;
        private final Object lock;

        /**
         * @param size Key size in bytes
         */
        MSEPrivateKey(int size, SecureRandom random) {
            this.value = generatePrivateKey(size, random);
            this.lock = new Object();
        }

        // private key: random N bit integer
        private BigInteger generatePrivateKey(int size, SecureRandom random) {
            byte[] bytes = new byte[size];
            for (int i = 0; i < size; i++) {
                bytes[i] = (byte) random.nextInt(256);
            }
            return BigIntegers.decodeUnsigned(ByteBuffer.wrap(bytes), size);
        }

        MSEPublicKey getPublicKey() {
            if (publicKey == null) {
                synchronized (lock) {
                    if (publicKey == null) {
                        publicKey = new MSEPublicKey(G.modPow(value, P));
                    }
                }
            }
            return publicKey;
        }

        BigInteger calculateSharedSecret(MSEPublicKey publicKey) {
            return publicKey.getValue().modPow(value, P);
        }

        @Override
        public String getAlgorithm() {
            return "DH";
        }

        @Override
        public String getFormat() {
            return null;
        }

        @Override
        public byte[] getEncoded() {
            return null;
        }
    }
}
