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

package bt.net.buffer;

import java.nio.Buffer;

/**
 * Re-usable buffer.
 *
 * Thread-safe, but not recommended to be used by any thread different from the one that created it.
 *
 * @since 1.6
 */
public interface BorrowedBuffer<T extends Buffer> {

    /**
     * Get the underlying buffer instance. It's strongly recommended to never
     * save the returned reference in object fields, variables or pass it via method parameters,
     * unless it is known for sure, that such field or variable will be short-lived
     * and used exclusively between calls to this method and {@link #unlock()}.
     *
     * Caller of this method SHOULD call {@link #unlock()} as soon as he's finished working with the buffer,
     * e.g. by using the same try-finally pattern as when working with locks:
     *
     * <blockquote><pre>
     *     BorrowedBuffer&lt;T&gt; holder = ...;
     *     T buffer = holder.lockAndGet();
     *     try {
     *         writeData(buffer);
     *     } finally {
     *         holder.unlock();
     *     }
     * </pre></blockquote>
     *
     * This method will block the calling thread until the buffer is in UNLOCKED state.
     *
     * @return Buffer or null if the buffer has already been released
     * @since 1.6
     */
    T lockAndGet();

    /**
     * Unlock the buffer, thus allowing to {@link #release()} it.
     *
     * @throws IllegalMonitorStateException if the buffer is not locked or is locked by a different thread
     * @since 1.6
     */
    void unlock();

    /**
     * Release the underlying buffer.
     *
     * The buffer will be returned to the pool of allocated but un-used buffers
     * and will eventually be garbage collected (releasing native memory in case of direct buffers)
     * or re-used in the form of another BorrowedBuffer instance.
     *
     * This method will block the calling thread until the buffer is in UNLOCKED state.
     *
     * This method has no effect, if the buffer has already been released.
     *
     * @since 1.6
     */
    void release();
}
