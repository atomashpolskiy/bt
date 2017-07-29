/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.utils.concurrent;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;

public class LoggingScheduledThreadPoolExecutor extends ScheduledThreadPoolExecutor {
	
	public static class NamedDaemonThreadFactory implements ThreadFactory {
		final String name;
		
		public NamedDaemonThreadFactory(String name) {
			this.name = name;
		}
		
		@Override
		public Thread newThread(Runnable r) {
			Thread t = new Thread(r);
			t.setDaemon(true);
			t.setName(name);
			return t;
		}
	}
	
	public static ThreadFactory namedDaemonFactory(String name) {
		return new NamedDaemonThreadFactory(name);
	}
	
	final Consumer<Throwable> exceptionHandler;

	public LoggingScheduledThreadPoolExecutor(int corePoolSize, ThreadFactory threadFactory, Consumer<Throwable> exceptionHandler) {
		super(corePoolSize, threadFactory);
		Objects.requireNonNull(exceptionHandler);
		this.exceptionHandler = exceptionHandler;
	}
	
	@Override
	protected void afterExecute(Runnable r, Throwable t) {
		super.afterExecute(r, t);
		
		if(r instanceof FutureTask<?>) {
			FutureTask<?> ft = (FutureTask<?>) r;
			if(ft.isDone() && !ft.isCancelled()) {
				try {
					ft.get();
				} catch (InterruptedException | ExecutionException e) {
					exceptionHandler.accept(e.getCause());
				}
			}
			
			if(t != null)
				exceptionHandler.accept(t);
			
		}
	}

}
