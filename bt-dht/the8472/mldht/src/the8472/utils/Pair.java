/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.utils;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class Pair<A,B> {
	
	public static <C,D> Pair<C,D> of(C a, D b) {
		return new Pair<C,D>(a, b);
	}
	
	public static <C,D> Function<D, Pair<C, D>> of(C a) {
		return b -> new Pair<C,D>(a,b) ;
	}
	
	public static <C,D> Consumer<Pair<C,D>> consume(final BiConsumer<C, D> cons) {
		return pair -> cons.accept(pair.a, pair.b);
	};
	
	public final A a;
	public final B b;
	
	
	public Pair(A a, B b) {
		this.a = a;
		this.b = b;
	}
	
	public A a() {
		return a;
	}
	
	public B b() {
		return b;
	}

}
