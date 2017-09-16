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
 *
 * <p>It determines which manifest to read version from
 * by testing if 'Bundle-SymbolicName' attribute is present in the manifest
 * and if its' value starts with 'com.github.atomashpolskiy.bt'.</p>
 *
 *<p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
public class ClasspathApplicationService implements ApplicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClasspathApplicationService.class);

    private static final String MANIFEST_FILE = "META-INF/MANIFEST.MF";

    private static final String BUNDLE_NAME_PREFIX = "com.github.atomashpolskiy.bt";
    private static final String BUNDLE_NAME_KEY = "Bundle-SymbolicName";

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
                LOGGER.debug("Processing manifest file: " + url.toExternalForm());
            }
            try (InputStream in = url.openStream()) {
                Manifest m = new Manifest(in);
                String bundleName = m.getMainAttributes().getValue(BUNDLE_NAME_KEY);
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(BUNDLE_NAME_KEY + ": " + bundleName);
                }
                if (bundleName != null && bundleName.startsWith(BUNDLE_NAME_PREFIX)) {
                    // found our manifest
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Will read version from manifest file: {}", url.toExternalForm());
                    }
                    manifest = m;
                    break;
                }
            } catch (Throwable e) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Failed to read manifest file: " + url.toExternalForm(), e);
                }
            }
        }

        if (manifest == null) {
            LOGGER.warn("Failed to determine version from manifest file -- application may be configured incorrectly");
            version = new Version(0, 0, false);
        } else {
            version = parseVersion(manifest.getMainAttributes().getValue(Name.IMPLEMENTATION_VERSION));
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Bt version {}", version);
            }
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
