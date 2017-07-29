/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.utils.concurrent;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Collection;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

/**
 * used a linked transfer queue for non-scheduled tasks to achieve high throughput / avoid lock contention.
 * 
 * scheduled tasks are handled by a dedicated runnable executed in one of the executor's threads
 * - gets re-circulated through the LTQ
 * - yields its own thread back to the executor when load depands it
 * - only ever that one instance active at a time -> sequential behavior -> we can use a lock-free priority queue
 * - wakeups happen either based on the next scheduled task or cooperatively with executor methods
 * 
 */
public class NonblockingScheduledExecutor implements ScheduledExecutorService {
	
	WrappedThreadPoolExecutor immediateExecutor;
	ThreadGroup group;
	PriorityQueue<RunnableScheduledFuture<?>> delayedTasks = new PriorityQueue<>();
	AtomicReference<Thread> currentSleeper = new AtomicReference<>();
	Queue<RunnableScheduledFuture<?>> submittedScheduledTasks = new ConcurrentLinkedQueue<>();
	BlockingQueue<Runnable> executorQueue = new LinkedTransferQueue<>();
	
	// there is only ever one single instance of this task in the pipeline. therefore anything done by it is guaranteed single-threaded execution
	final Runnable scheduler = this::doStateMaintenance;
	final Thread.UncaughtExceptionHandler exceptionHandler;
	
	private class WrappedThreadPoolExecutor extends ThreadPoolExecutor {

		public WrappedThreadPoolExecutor(int poolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
			super(poolSize, poolSize, keepAliveTime, unit, workQueue);

		}

		@Override
		public void execute(Runnable command) {
			super.execute(command);
			wakeupWaiter(false);
		}
		
		void executeWithoutWakeup(Runnable command) {
			super.execute(command);
		}
		
		@Override
		protected void afterExecute(Runnable r, Throwable t) {
			super.afterExecute(r, t);
			
			if(exceptionHandler != null && r instanceof FutureTask<?>) {
				FutureTask<?> ft = (FutureTask<?>) r;
				if(ft.isDone() && !ft.isCancelled()) {
					try {
						ft.get();
					} catch (InterruptedException | ExecutionException e) {
						exceptionHandler.uncaughtException(null, e.getCause());
					}
				}
				
			}
		}
		
	}
	
	public NonblockingScheduledExecutor(String name, int threadCount, UncaughtExceptionHandler handler) {
		
		this.exceptionHandler = handler;
		group = new ThreadGroup(name);
		
		group.setDaemon(true);
		
		ThreadFactory f = (r) -> {
			Thread t = new Thread(group, r);
			if(handler != null)
				t.setUncaughtExceptionHandler(handler);
			t.setDaemon(true);
			t.setName(name);
			return t;
		};
		
		
		immediateExecutor = new WrappedThreadPoolExecutor(threadCount, 4, TimeUnit.SECONDS, executorQueue);
		immediateExecutor.setThreadFactory(f);
		immediateExecutor.execute(scheduler);
	}
	
	
	void doStateMaintenance() {
		
		while(!isShutdown()) {
			RunnableScheduledFuture<?> toSchedule;
			while((toSchedule = submittedScheduledTasks.poll()) != null)
				delayedTasks.add(toSchedule);
			RunnableScheduledFuture<?> toExecute;
			while((toExecute = delayedTasks.peek()) != null && toExecute.getDelay(TimeUnit.NANOSECONDS) <= 0) {
				delayedTasks.poll();
				immediateExecutor.executeWithoutWakeup(toExecute);
			}
			
			RunnableScheduledFuture<?> nextTask = delayedTasks.peek();

			// signal current thread as suspended before we actually check work queues.
			// this avoids wakeupWaiter() seeing an inconsistent state
			currentSleeper.set(Thread.currentThread());

			if(executorQueue.isEmpty() && submittedScheduledTasks.isEmpty()) {
				if(nextTask != null)
					LockSupport.parkNanos(nextTask.getDelay(TimeUnit.NANOSECONDS));
				else
					LockSupport.park();
				currentSleeper.set(null);
			} else {
				currentSleeper.set(null);
				// there are unmatched tasks in the queue, return this thread to the pool
				break;
			}
		}
		
		
		// reschedule if we fall out of loop
		if(!isShutdown())
			immediateExecutor.executeWithoutWakeup(scheduler);
	}
	
	void wakeupWaiter(boolean forScheduled) {
		Thread t;
		while((t = currentSleeper.get()) != null && (forScheduled ? !submittedScheduledTasks.isEmpty() : !executorQueue.isEmpty())) {
			if(currentSleeper.compareAndSet(t, null)) {
				LockSupport.unpark(t);
				break;
			}
		}
	}

	@Override
	public void shutdown() {
		immediateExecutor.shutdown();
		wakeupWaiter(true);
	}

	@Override
	public List<Runnable> shutdownNow() {
		List<Runnable> l = immediateExecutor.shutdownNow();
		wakeupWaiter(true);
		return l;
	}

	@Override
	public boolean isShutdown() {
		return immediateExecutor.isShutdown();
	}

	@Override
	public boolean isTerminated() {
		return immediateExecutor.isTerminated();
	}

	@Override
	public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
		return immediateExecutor.awaitTermination(timeout, unit);
	}

	@Override
	public <T> Future<T> submit(Callable<T> task) {
		return immediateExecutor.submit(task);
	}

	@Override
	public <T> Future<T> submit(Runnable task, T result) {
		return immediateExecutor.submit(task, result);
	}

	@Override
	public Future<?> submit(Runnable task) {
		return immediateExecutor.submit(task);
	}

	@Override
	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
		return immediateExecutor.invokeAll(tasks);
	}

	@Override
	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
		return immediateExecutor.invokeAll(tasks, timeout, unit);
	}

	@Override
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
		return immediateExecutor.invokeAny(tasks);
	}

	@Override
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
		return immediateExecutor.invokeAny(tasks, timeout, unit);
	}

	@Override
	public void execute(Runnable command) {
		immediateExecutor.execute(command);
	}
	
	private class SchedF<T> extends FutureTask<T> implements RunnableScheduledFuture<T> {
		
		long nanos;
		final long period;
		
		public SchedF(Runnable r, long delay, TimeUnit u) {
			super(r,null);
			this.nanos =  System.nanoTime() + TimeUnit.NANOSECONDS.convert(delay, u);
			this.period = 0;
		}
		
		public SchedF(Callable<T> callable, long delay, TimeUnit u) {
			super(callable);
			this.nanos =  System.nanoTime() + TimeUnit.NANOSECONDS.convert(delay, u);
			period = 0;
		}



		public SchedF(Runnable command, long initialDelay, long period, TimeUnit unit) {
			super(command, null);
			this.nanos =  System.nanoTime() + TimeUnit.NANOSECONDS.convert(initialDelay, unit);
			this.period = TimeUnit.NANOSECONDS.convert(period, unit);
		}




		@Override
		public long getDelay(TimeUnit unit) {
			return unit.convert(nanos - System.nanoTime(), TimeUnit.NANOSECONDS);
		}

		@Override
		public int compareTo(Delayed o) {
			long diff;
			if(o instanceof SchedF) {
				SchedF<?> otherS = (SchedF<?>) o;
				diff = this.nanos - otherS.nanos;
			} else {
				diff = getDelay(TimeUnit.NANOSECONDS) - o.getDelay(TimeUnit.NANOSECONDS);
			}
			
			return (diff == 0) ? 0 : (diff < 0) ? -1 : 1;
		}

		@Override
		public boolean isPeriodic() {
			return period != 0;
		}
		
		void recalcTime() {
			if(period < 0) {
				nanos = System.nanoTime() + (-period);
			} else {
				nanos += period;
			}
		}
		
		@Override
		public void run() {
			if(isPeriodic()) {
				if(runAndReset()) {
					recalcTime();
					submittedScheduledTasks.add(this);
					wakeupWaiter(true);
				}
			} else {
				super.run();
			}
				
		}
		
	}


	@Override
	public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
		// roll our own to avoid thread suspension due to queue locking when hammering the scheduler with lots of one-off submissions
		SchedF<?> future = new SchedF<Void>(command, delay, unit);

		submittedScheduledTasks.add(future);
		wakeupWaiter(true);
		
		return future;
	}

	@Override
	public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
		SchedF<V> future = new SchedF<>(callable, delay, unit);
		
		submittedScheduledTasks.add(future);
		wakeupWaiter(true);
		
		return future;
	}

	@Override
	public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
		if(period < 0)
			throw new IllegalArgumentException("delay must be non-negative");
		SchedF<?> future = new SchedF<Void>(command, initialDelay, period, unit);
		
		submittedScheduledTasks.add(future);
		wakeupWaiter(true);
		
		return future;
	}

	@Override
	public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
		if(delay < 0)
			throw new IllegalArgumentException("delay must be non-negative");
		SchedF<?> future = new SchedF<Void>(command, initialDelay, -delay, unit);
		
		submittedScheduledTasks.add(future);
		wakeupWaiter(true);
		
		return future;
	}

}
