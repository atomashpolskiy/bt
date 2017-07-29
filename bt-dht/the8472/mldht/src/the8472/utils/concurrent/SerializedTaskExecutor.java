/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.utils.concurrent;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class SerializedTaskExecutor {
	
	public static <T> Consumer<T> runSerialized(Consumer<T> task) {
		AtomicBoolean lock = new AtomicBoolean();
		Queue<T> q = new ConcurrentLinkedQueue<>();
		
		Predicate<T> tryRun = (T toTry) -> {
			boolean success = false;
			while(lock.compareAndSet(false, true)) {
				try {
					if(toTry != null) {
						task.accept(toTry);
						success = true;
					}
					T other;
					while((other = q.poll()) != null)
						task.accept(other);
				} finally {
					lock.set(false);
				}

				if(q.peek() == null)
					break;
			}
			return success;
		};
		
		return (T r) -> {
			
			// attempt to execute on current thread
			if(lock.get() == false && tryRun.test(r))
				return;// success
			
			// execution on current thread failed, enqueue
			q.add(r);
			// try again in case other thread ceased draining the queue
			if (lock.get() == false)
				tryRun.test(null);
			
		};
	}
	
	public static Runnable onceMore(Runnable loopBody) {
		AtomicInteger lock = new AtomicInteger();
		
		return () -> {

			// request execution of the runnable
			int current = lock.incrementAndGet();
			
			// another thread is executing
			if(current > 1)
				return;
			
			try {
				do {
					loopBody.run();
					
					current = lock.addAndGet(Math.negateExact(current));
				} while(current > 0);
			} catch(Throwable t) {
				lock.set(0);
				throw t;
			}
		};
	}

}
