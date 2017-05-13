package bt.net.crypto;

import bt.protocol.Protocols;
import bt.protocol.crypto.EncryptionPolicy;

import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.Random;

public class NegotiateEncryptionPolicy {

    private final EncryptionPolicy encryptionPolicy;

    public NegotiateEncryptionPolicy(EncryptionPolicy encryptionPolicy) {
        this.encryptionPolicy = encryptionPolicy;
    }

    public EncryptionPolicy negotiateIncoming(ByteChannel channel, ByteBuffer buf) {
        if (buf.remaining() > 16 + 512) {
            throw new IllegalArgumentException("Too much initial data");
        }

        EncryptionPolicy negotiatedEncryptionPolicy;
        int min = Math.max(16 - buf.remaining(), 0);
        int limit = 16 + 512 - buf.remaining();

        // 3. ...
        // - ENCRYPT(VC, crypto_provide, len(PadC), PadC, len(IA))
        if (limit > 0) { // if limit is 0 then all possible data has already been received
            int offset = buf.position();
            buf.position(buf.limit());
            buf.limit(buf.capacity());
            new ReceiveData(channel, Duration.ofSeconds(30)).execute(buf, min, limit);
            buf.flip();
            buf.position(offset);
        }

        byte[] VC = getVerificationConstant();
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
        bytes[0] = buf.get();
        bytes[1] = buf.get();
        int theirPadding = Protocols.readShort(bytes, 0);
        // assume that all data has been received, so the whole padding block is present
        for (int i = 0; i < theirPadding; i++) {
            buf.get(); // replace with check for remaining bytes
        }

        // skip Initial Payload length
        buf.get(); // replace with check for remaining bytes
        buf.get(); // replace with check for remaining bytes
        if (buf.hasRemaining()) {
            throw new RuntimeException("should not happen");
        }

        // 4. B->A:
        // - ENCRYPT(VC, crypto_select, len(padD), padD)
        // - ENCRYPT2(Payload Stream)
        buf.clear();
        buf.put(VC);
        buf.put(getCryptoProvideBitfield(negotiatedEncryptionPolicy));
        byte[] padding = getZeroPadding(512);
        buf.put(Protocols.getShortBytes(padding.length));
        buf.put(padding);

        buf.flip();
        new SendData(channel).execute(buf);
        buf.clear();

        return negotiatedEncryptionPolicy;
    }

    public EncryptionPolicy negotiateOutgoing(ByteChannel channel, ByteBuffer buf) {
        EncryptionPolicy negotiatedEncryptionPolicy;
        // - ENCRYPT(VC, crypto_provide, len(PadC), PadC, len(IA))
        buf.clear();
        byte[] VC = getVerificationConstant();
        buf.put(VC);
        buf.put(getCryptoProvideBitfield(encryptionPolicy));
        byte[] padding = getZeroPadding(512);
        buf.put(Protocols.getShortBytes(padding.length));
        buf.put(padding);
        // - ENCRYPT(IA)
        // do not write IA (initial payload data) for now, wait for encryption negotiation
        buf.put((byte) 0); // IA length = 0
        buf.put((byte) 0);

        buf.flip();
        new SendData(channel).execute(buf);
        buf.clear();

        new ReceiveData(channel, Duration.ofSeconds(30)).execute(buf, 14, 14 + 512);

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
        bytes[0] = buf.get();
        bytes[1] = buf.get();
        int theirPadding = Protocols.readShort(bytes, 0);
//        buf.limit(received);
//        buf.position(14 + theirPadding);
        // assume that all data has been received, so the whole padding block is present
        for (int i = 0; i < theirPadding; i++) {
            buf.get(); // replace with check for remaining bytes
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
//        return new byte[r.nextInt(length + 1)];
        return new byte[0];
    }
}
