/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.utils;

import static java.util.Collections.emptyList;
import static java.util.concurrent.CompletableFuture.completedFuture;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class Functional {
	
	public static <T> T tap(T obj, Consumer<T> c) {
		c.accept(obj);
		return obj;
	}
	
	public static <T, E extends Throwable> T tapThrow(T obj, ThrowingConsumer<T, E> c) throws E  {
		c.accept(obj);
		return obj;
	}
	
	public static interface ThrowingConsumer<T,E extends Throwable> {
		void accept(T arg) throws E;
	}
	
	@FunctionalInterface
	public static interface ThrowingSupplier<T,E extends Throwable> {
		T get() throws E;
	}
	
	@FunctionalInterface
	public static interface ThrowingFunction<R, T, E extends Throwable> {
		R apply(T arg) throws E;
	}
	
	public static <T> T unchecked(ThrowingSupplier<? extends T, ?> f) {
		try {
			return f.get();
		} catch (Throwable e) {
			throwAsUnchecked(e);
			return null;
		}
	}
	
	public static <IN, OUT> OUT sync(IN obj, Function<IN, OUT> supp) {
		synchronized(obj) {
			return supp.apply(obj);
		}
	}
	
	public static <RES, T extends AutoCloseable> RES autoclose(Supplier<T> s, Function<T,RES> f) throws Exception {
		RES result;
		try(T t = s.get()) {
			result = f.apply(t);
		}
		
		return result;
	}
	
	public static <R,T> Function<T,R> unchecked(ThrowingFunction<R,T,?> f) {
		return (arg) -> {
			try {
				return f.apply(arg);
			} catch(Throwable e) {
				throwAsUnchecked(e);
				return null;
			}
		};
	}
	
	public static <T> Consumer<T> uncheckedCons(ThrowingConsumer<T, ?> cons) {
		return (arg) -> {
			try {
				cons.accept(arg);
			} catch (Throwable e) {
				throwAsUnchecked(e);
				return;
			}
		};
	}
	
	
	public static <T> CompletionStage<List<T>> awaitAll(Collection<? extends CompletionStage<T>> stages) {
		return stages.stream().map(st -> st.thenApply(Collections::singletonList)).reduce(completedFuture(emptyList()), (f1, f2) -> f1.thenCombine(f2, (a, b) -> tap(new ArrayList<>(a), l -> l.addAll(b))));
	}
	
	public static <IN, OUT, EX extends Throwable> Function<IN, OUT> castOrThrow(Class<OUT> type, Function<IN, EX> ex) {
		return (in) -> {
			if(!type.isInstance(in))
				throwAsUnchecked(ex.apply(in));
			return type.cast(in);
		};
	}

	
	
	public static void throwAsUnchecked(Throwable t) {
	    Thrower.asUnchecked(t);
	}
	
	private static class Thrower {

		@SuppressWarnings("unchecked")
		static private <T extends Throwable> void asUnchecked(Throwable t) throws T {
		    throw (T) t;
		}
	}
	
	@SuppressWarnings("unchecked") // a supertype of the expected T can be passed to allow type-inference to match erased generics
	public static <K, T> Optional<T> typedGet(Map<? super K, ?> map, K key, Class<? super T> clazz) {
		return (Optional<T>) Optional.ofNullable(map.get(key)).filter(clazz::isInstance).map(clazz::cast);
	}


	static class  ShortCircuitFlatMapSpliterator<R,T> extends Spliterators.AbstractSpliterator<R> {
		
		Spliterator<T> sourceSpliterator;
		Stream<R> currentSubStream;
		Spliterator<R> currentSubSpliterator;
		Function<T, Stream<R>> flatMapper;

		protected ShortCircuitFlatMapSpliterator(Spliterator<T> source, Function<T,Stream<R>> flatMapper) {
			super(source.estimateSize(), 0);
			sourceSpliterator = source;
			this.flatMapper = flatMapper;
		}

		@Override
		public boolean tryAdvance(Consumer<? super R> action) {
			for(;;) {
				if(currentSubSpliterator == null) {
					if(!sourceSpliterator.tryAdvance(t -> {
						currentSubStream = flatMapper.apply(t);
					}))
						return false;
					
					if(currentSubStream == null)
						continue;

					currentSubSpliterator = currentSubStream.spliterator();

				}
				
				if(currentSubSpliterator.tryAdvance(action))
					return true;


				currentSubSpliterator = null;
				currentSubStream.close();
			}
		}
		
		void close() {
			if(currentSubStream != null) {
				currentSubStream.close();
			}
		}


		
	}
	
	/**
	 * workaround for https://bugs.openjdk.java.net/browse/JDK-8075939
	 */
	public static <R, T> Stream<R> shortCircuitingflatMap(Stream<T> st, Function<T, Stream<R>> flatMapper) {
		Spliterator<T> sourceSpliterator = st.spliterator();
		
		ShortCircuitFlatMapSpliterator<R,T> sinkSpliterator = new ShortCircuitFlatMapSpliterator<>(sourceSpliterator, flatMapper);
		Stream<R> resultStream = StreamSupport.stream(sinkSpliterator, false);
		
		resultStream = resultStream.onClose(st::close).onClose(sinkSpliterator::close);
		
		
		return resultStream;
	}
	


}
