package bt.protocol.extended;

import bt.bencoding.model.BEInteger;
import bt.bencoding.model.BEString;
import bt.metainfo.TorrentId;
import bt.protocol.IExtendedHandshakeFactory;
import bt.protocol.crypto.EncryptionPolicy;
import bt.runtime.Config;
import bt.service.ApplicationService;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 *<p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
public class ExtendedHandshakeFactory implements IExtendedHandshakeFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExtendedHandshakeFactory.class);

    private static final String ENCRYPTION_PROPERTY = "e";
    private static final String TCPPORT_PROPERTY = "p";
    private static final String VERSION_PROPERTY = "v";

    private static final String VERSION_TEMPLATE = "Bt %s";

    private final ExtendedMessageTypeMapping messageTypeMapping;
    private final ApplicationService applicationService;
    private final EncryptionPolicy encryptionPolicy;
    private final int tcpAcceptorPort;

    private final ConcurrentMap<TorrentId, ExtendedHandshake> extendedHandshakes;

    @Inject
    public ExtendedHandshakeFactory(ExtendedMessageTypeMapping messageTypeMapping,
                                    ApplicationService applicationService,
                                    Config config) {
        this.messageTypeMapping = messageTypeMapping;
        this.applicationService = applicationService;
        this.encryptionPolicy = config.getEncryptionPolicy();
        this.tcpAcceptorPort = config.getAcceptorPort();
        this.extendedHandshakes = new ConcurrentHashMap<>();
    }

    @Override
    public ExtendedHandshake getHandshake(TorrentId torrentId) {
        ExtendedHandshake handshake = extendedHandshakes.get(torrentId);
        if (handshake == null) {
            handshake = buildHandshake(torrentId);
            ExtendedHandshake existing = extendedHandshakes.putIfAbsent(torrentId, handshake);
            if (existing != null) {
                handshake = existing;
            }
        }
        return handshake;
    }

    private ExtendedHandshake buildHandshake(TorrentId torrentId) {
        ExtendedHandshake.Builder builder = ExtendedHandshake.builder();

        switch (encryptionPolicy) {
            case REQUIRE_PLAINTEXT:
            case PREFER_PLAINTEXT: {
                builder.property(ENCRYPTION_PROPERTY, new BEInteger(null, BigInteger.ZERO));
            }
            case PREFER_ENCRYPTED:
            case REQUIRE_ENCRYPTED: {
                builder.property(ENCRYPTION_PROPERTY, new BEInteger(null, BigInteger.ONE));
            }
            default: {
                // do nothing
            }
        }

        builder.property(TCPPORT_PROPERTY, new BEInteger(null, BigInteger.valueOf(tcpAcceptorPort)));

        // TODO: metadata_size

        String version;
        try {
            version = getVersion();
        } catch (Exception e) {
            LOGGER.error("Failed to get version", e);
            version = getDefaultVersion();
        }
        builder.property(VERSION_PROPERTY, new BEString(version.getBytes(Charset.forName("UTF-8"))));

        messageTypeMapping.visitMappings(builder::addMessageType);
        return builder.build();
    }

    protected String getVersion() {
        return String.format(VERSION_TEMPLATE, applicationService.getVersion());
    }

    private String getDefaultVersion() {
        return String.format(VERSION_TEMPLATE, "(unknown version)");
    }
}
