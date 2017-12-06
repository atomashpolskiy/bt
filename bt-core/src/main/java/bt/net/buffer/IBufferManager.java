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

import java.nio.ByteBuffer;

/**
 * Provides the means to temporarily borrow a direct buffer.
 *
 * It maintains a pool of unused buffers and for each borrow request decides whether it should:
 * - create and return a new buffer, if there are no more unused buffers left
 * - return an existing unused buffer, which has been returned to the pool by the previous borrower
 *
 * After the borrower is done with the buffer, he should invoke
 * {@link BorrowedBuffer#release()} to return the buffer to the pool.
 *
 * @since 1.6
 */
public interface IBufferManager {

    /**
     * Temporarily borrow a direct byte buffer.
     *
     * After the borrower is done with the buffer, he should invoke
     * {@link BorrowedBuffer#release()} to return the buffer to the pool.
     *
     * @since 1.6
     */
    BorrowedBuffer<ByteBuffer> borrowByteBuffer();
}
