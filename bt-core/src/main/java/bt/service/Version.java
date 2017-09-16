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

/**
 * Application version info.
 *
 * @since 1.0
 */
public class Version {

    private final int major;
    private final int minor;
    private final boolean snapshot;

    /**
     * @since 1.0
     */
    public Version(int major, int minor, boolean snapshot) {
        this.major = major;
        this.minor = minor;
        this.snapshot = snapshot;
    }

    /**
     * @since 1.0
     */
    public int getMajor() {
        return major;
    }

    /**
     * @since 1.0
     */
    public int getMinor() {
        return minor;
    }

    /**
     * @since 1.0
     */
    public boolean isSnapshot() {
        return snapshot;
    }

    @Override
    public String toString() {
        String version = major + "." + minor;
        if (snapshot) {
            version += " (Snapshot)";
        }
        return version;
    }
}
