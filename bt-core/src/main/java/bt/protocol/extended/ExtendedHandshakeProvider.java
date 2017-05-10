package bt.protocol.extended;

import bt.bencoding.model.BEInteger;
import bt.bencoding.model.BEString;
import bt.protocol.crypto.EncryptionPolicy;
import bt.runtime.Config;
import bt.service.ApplicationService;
import com.google.inject.Inject;
import com.google.inject.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.nio.charset.Charset;

/**
 *<p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
public class ExtendedHandshakeProvider implements Provider<ExtendedHandshake> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExtendedHandshakeProvider.class);

    private static final String ENCRYPTION_PROPERTY = "e";
    private static final String TCPPORT_PROPERTY = "p";
    private static final String VERSION_PROPERTY = "v";

    private static final String VERSION_TEMPLATE = "Bt %s";

    private final ExtendedMessageTypeMapping messageTypeMapping;
    private final ApplicationService applicationService;
    private final EncryptionPolicy encryptionPolicy;
    private final int tcpAcceptorPort;

    private volatile ExtendedHandshake extendedHandshake;
    private final Object lock;

    @Inject
    public ExtendedHandshakeProvider(ExtendedMessageTypeMapping messageTypeMapping,
                                     ApplicationService applicationService,
                                     Config config) {
        this.messageTypeMapping = messageTypeMapping;
        this.applicationService = applicationService;
        this.encryptionPolicy = config.getEncryptionPolicy();
        this.tcpAcceptorPort = config.getAcceptorPort();
        this.lock = new Object();
    }

    @Override
    public ExtendedHandshake get() {
        if (extendedHandshake == null) {
            synchronized (lock) {
                if (extendedHandshake == null) {
                    extendedHandshake = buildHandshake();
                }
            }
        }
        return extendedHandshake;
    }

    private ExtendedHandshake buildHandshake() {
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
