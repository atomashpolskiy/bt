package bt.service;

import bt.BtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.jar.Attributes.Name;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads application version info from a manifest file,
 * located on the application's classpath.
 * It determines which manifest to read version from
 * by testing if 'Bundle-SymbolicName' attribute is present in the manifest
 * and if its' value is equal to 'bt.core'.
 *
 * @since 1.0
 */
public class ClasspathApplicationService implements IApplicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClasspathApplicationService.class);

    private static final String MANIFEST_FILE = "META-INF/MANIFEST.MF";

    private static final String BT_BUNDLE_NAME = "bt.core";
    private static final String BUNDLE_NAME = "Bundle-SymbolicName";

    private final Version version;

    public ClasspathApplicationService() {

        Enumeration<URL> urls;
        try {
            urls = ClasspathApplicationService.class.getClassLoader().getResources(MANIFEST_FILE);
        } catch (IOException e) {
            throw new BtException("Failed to read application version", e);
        }

        Manifest manifest = null;
        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Processing manifest: " + url.toExternalForm());
            }
            try (InputStream in = url.openStream()) {
                Manifest m = new Manifest(in);
                String bundleName = m.getMainAttributes().getValue(BUNDLE_NAME);
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(BUNDLE_NAME + ": " + bundleName);
                }
                if (bundleName != null && bundleName.equals(BT_BUNDLE_NAME)) {
                    // found our manifest
                    manifest = m;
                    break;
                }
            } catch (Throwable e) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Failed to read manifest: " + url.toExternalForm(), e);
                }
            }
        }

        if (manifest == null) {
            LOGGER.warn("Failed to find manifest file -- application may be configured incorrectly");
            version = new Version(0, 0, false);
        } else {
            version = parseVersion(manifest.getMainAttributes().getValue(Name.IMPLEMENTATION_VERSION));
        }
    }

    private Version parseVersion(String versionStr) {

        Pattern pattern = Pattern.compile("^(\\d+)[.](\\d+).*");
        Matcher matcher = pattern.matcher(versionStr);
        if (!matcher.matches()) {
            throw new BtException("Invalid version: " + versionStr);
        }

        int major, minor;
        try {
            major = Integer.valueOf(matcher.group(1));
            minor = Integer.valueOf(matcher.group(2));
        } catch (NumberFormatException e) {
            throw new BtException("Invalid version: " + versionStr);
        }

        boolean snapshot = versionStr.endsWith("-SNAPSHOT");
        return new Version(major, minor, snapshot);
    }

    @Override
    public Version getVersion() {
        return version;
    }
}
