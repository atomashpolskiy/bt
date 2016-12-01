package bt.service;

import bt.BtException;
import bt.net.PeerId;
import bt.tracker.SecretKey;
import com.google.inject.Inject;

import java.util.Optional;
import java.util.Random;

/**
 *<p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
public class VersionAwareIdentityService implements IdentityService {

    private final PeerId peerId;

    @Inject
    public VersionAwareIdentityService(ApplicationService applicationService) {
        peerId = buildPeerId(buildVersionPrefix(applicationService.getVersion()));
    }

    private byte[] buildVersionPrefix(Version version) {

        int major = version.getMajor();
        if (major > Byte.MAX_VALUE) {
            throw new BtException("Invalid major version: " + major);
        }

        int minor = version.getMinor();
        if (minor > Byte.MAX_VALUE) {
            throw new BtException("Invalid major version: " + minor);
        }

        boolean snapshot = version.isSnapshot();
        return new byte[]{'-','B','t', (byte) major, (byte) minor,0, (byte) (snapshot? 1 : 0),'-'};
    }

    protected PeerId buildPeerId(byte[] versionPrefix) {

        if (versionPrefix.length >= PeerId.length()) {
            throw new IllegalArgumentException("Prefix is too long: " + versionPrefix.length);
        }

        byte[] tail = new byte[PeerId.length() - versionPrefix.length];
        Random random = new Random(System.currentTimeMillis());
        random.nextBytes(tail);

        byte[] peerId = new byte[PeerId.length()];
        System.arraycopy(versionPrefix, 0, peerId, 0, versionPrefix.length);
        System.arraycopy(tail, 0, peerId, versionPrefix.length, tail.length);
        return PeerId.fromBytes(peerId);
    }

    @Override
    public PeerId getLocalPeerId() {
        return peerId;
    }

    @Override
    public Optional<SecretKey> getSecretKey() {
        return Optional.empty();
    }
}
