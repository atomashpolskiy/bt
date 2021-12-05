/*
 * Copyright (c) 2016—2021 Andrei Tomashpolskiy and individual contributors.
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

package bt.data.digest;

public class SHA1Digester {

    // Linux readahead sequential access detection is smart, so we can use a small buffer. Mac OS seems less intelligent
    // and a bigger buffer helps performance in cases where the file is not in the RAM Disk Cache. Windows has not been
    // tested.
    // In the future, a call to posix_fadvise on Linux and fcntl on Mac could eliminate the need for this. But there's
    // no infrastructure for native OS calls at the moment.
    // See:
    // https://man7.org/linux/man-pages/man2/posix_fadvise.2.html
    // https://developer.apple.com/library/archive/documentation/System/Conceptual/ManPages_iPhoneOS/man2/fcntl.2.html
    private static final int LINUX_DEFAULT_BUFFER_SIZE = 16 * 1024;
    private static final int OTHER_OS_DEFAULT_BUFFER_SIZE = 1 * 1024 * 1024;

    public static final int DEFAULT_BUFFER_SIZE =
            System.getProperty("os.name").contains("Linux") ? LINUX_DEFAULT_BUFFER_SIZE : OTHER_OS_DEFAULT_BUFFER_SIZE;

    /**
     * Creates a new Digester with a default buffer size
     *
     * @return a new Digester with a reasonable default buffer size
     */
    public static Digester newDigester() {
        return newDigester(DEFAULT_BUFFER_SIZE);
    }

    /**
     * Creates a new Digester with a default step size
     *
     * @return a new Digester with a reasonable default step size
     */
    public static Digester newDigester(int bufferSize) {
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("Invalid bufferSize: " + bufferSize);
        }
        return new JavaSecurityDigester("SHA-1", bufferSize);
    }
}
