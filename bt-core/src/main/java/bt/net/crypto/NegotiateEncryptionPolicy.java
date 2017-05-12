package bt.net.crypto;

import bt.protocol.Protocols;
import bt.protocol.crypto.EncryptionPolicy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.Random;

public class NegotiateEncryptionPolicy {

    private final EncryptionPolicy encryptionPolicy;

    public NegotiateEncryptionPolicy(EncryptionPolicy encryptionPolicy) {
        this.encryptionPolicy = encryptionPolicy;
    }

    public EncryptionPolicy negotiateIncoming(InputStream in, OutputStream out) {
        EncryptionPolicy negotiatedEncryptionPolicy;
        try {
            byte[] VC = getVerificationConstant();
            // 3. ...
            // - ENCRYPT(VC, crypto_provide, len(PadC), PadC, len(IA))
            ByteBuffer buf = ByteBuffer.allocate(2048); // temp
            new ReceiveData(in, Duration.ofSeconds(30)).execute(buf, 16, 16 + 512);

            buf.flip();

            byte[] theirVC = new byte[8];
            buf.get(theirVC);
            if (!Arrays.equals(VC, theirVC)) {
                throw new IllegalStateException("Invalid VC: "+ Arrays.toString(theirVC));
            }

            byte[] crypto_provide = new byte[4];
            buf.get(crypto_provide);
            negotiatedEncryptionPolicy = selectPolicy(crypto_provide, encryptionPolicy)
                    .orElseThrow(() -> new IllegalStateException("Failed to negotiate the encryption policy"));

            byte[] bytes = new byte[2];
            bytes[0] = (byte) in.read();
            bytes[1] = (byte) in.read();
            int theirPadding = Protocols.readShort(bytes, 0);
            // assume that all data has been received, so the whole padding block is present
            for (int i = 0; i < theirPadding; i++) {
                buf.get(); // replace with check for remaining bytes
            }

            // skip Initial Payload length
            buf.get(); // replace with check for remaining bytes
            buf.get(); // replace with check for remaining bytes

            // 4. B->A:
            // - ENCRYPT(VC, crypto_select, len(padD), padD)
            // - ENCRYPT2(Payload Stream)
            out.write(VC);
            out.write(getCryptoProvideBitfield(negotiatedEncryptionPolicy));
            byte[] padding = getZeroPadding(512);
            out.write(Protocols.getShortBytes(padding.length));
            out.write(padding);
            out.flush();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return negotiatedEncryptionPolicy;
    }

    public EncryptionPolicy negotiateOutgoing(InputStream in, OutputStream out) {
        EncryptionPolicy negotiatedEncryptionPolicy;
        try {
            // - ENCRYPT(VC, crypto_provide, len(PadC), PadC, len(IA))
            byte[] VC = getVerificationConstant();
            out.write(VC);
            out.write(getCryptoProvideBitfield(encryptionPolicy));
            byte[] padding = getZeroPadding(512);
            out.write(Protocols.getShortBytes(padding.length));
            out.write(padding);
            // - ENCRYPT(IA)
            // do not write IA (initial payload data) for now, wait for encryption negotiation
            out.write(0); // IA length = 0
            out.write(0);
            out.flush();

            ByteBuffer buf = ByteBuffer.allocate(2048); // temp
            new ReceiveData(in, Duration.ofSeconds(30)).execute(buf, 14, 14 + 512);

            // 4. B->A:
            // - ENCRYPT(VC, crypto_select, len(padD), padD)
            // - ENCRYPT2(Payload Stream)
            buf.flip();

            byte[] theirVC = new byte[8];
            buf.get(theirVC);
            if (!Arrays.equals(VC, theirVC)) {
                throw new IllegalStateException("Invalid VC: " + Arrays.toString(theirVC));
            }
            byte[] crypto_select = new byte[4];
            buf.get(crypto_select);
            negotiatedEncryptionPolicy = selectPolicy(crypto_select, encryptionPolicy)
                    .orElseThrow(() -> new IllegalStateException("Failed to negotiate the encryption policy"));
            byte[] bytes = new byte[2];
            bytes[0] = (byte) in.read();
            bytes[1] = (byte) in.read();
            int theirPadding = Protocols.readShort(bytes, 0);
//        buf.limit(received);
//        buf.position(14 + theirPadding);
            // assume that all data has been received, so the whole padding block is present
            for (int i = 0; i < theirPadding; i++) {
                buf.get(); // replace with check for remaining bytes
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return negotiatedEncryptionPolicy;
    }

    private byte[] getVerificationConstant() {
        return new byte[8];
    }

    private byte[] getCryptoProvideBitfield(EncryptionPolicy encryptionPolicy) {
        byte[] crypto_provide = new byte[4];
        switch (encryptionPolicy) {
            case REQUIRE_PLAINTEXT: {
                crypto_provide[3] = 1; // only 0x01
                break;
            }
            case PREFER_PLAINTEXT:
            case PREFER_ENCRYPTED: {
                crypto_provide[3] = 3; // both 0x01 and 0x02
                break;
            }
            case REQUIRE_ENCRYPTED: {
                crypto_provide[3] = 2; // only 0x02
                break;
            }
            default: {
                // do nothing, bitfield is all zeros
            }
        }
        return crypto_provide;
    }

    private Optional<EncryptionPolicy> selectPolicy(byte[] crypto_provide, EncryptionPolicy localEncryptionPolicy) {
        boolean plaintextProvided = (crypto_provide[3] & 0x01) == 0x01;
        boolean encryptionProvided = (crypto_provide[3] & 0x02) == 0x02;
        if (!plaintextProvided && !encryptionProvided) {
            return Optional.empty();
        }
        switch (localEncryptionPolicy) {
            case REQUIRE_PLAINTEXT: {
                return plaintextProvided? Optional.of(EncryptionPolicy.REQUIRE_PLAINTEXT) : Optional.empty();
            }
            case PREFER_PLAINTEXT: {
                return plaintextProvided? Optional.of(EncryptionPolicy.REQUIRE_PLAINTEXT)
                        : Optional.of(EncryptionPolicy.REQUIRE_ENCRYPTED);
            }
            case PREFER_ENCRYPTED: {
                return encryptionProvided? Optional.of(EncryptionPolicy.REQUIRE_ENCRYPTED)
                        : Optional.of(EncryptionPolicy.REQUIRE_PLAINTEXT);
            }
            case REQUIRE_ENCRYPTED: {
                return encryptionProvided? Optional.of(EncryptionPolicy.REQUIRE_ENCRYPTED) : Optional.empty();
            }
            default: {
                throw new IllegalStateException("Unknown encryption policy: " + localEncryptionPolicy.name());
            }
        }
    }

    private byte[] getZeroPadding(int length) {
        // todo: use this constructor everywhere in the project
        Random r = new Random();
        return new byte[r.nextInt(length + 1)];
    }
}
