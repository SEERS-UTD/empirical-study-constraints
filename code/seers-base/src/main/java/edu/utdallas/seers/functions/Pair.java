package edu.utdallas.seers.functions;

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * This class is exclusively to be used with the methods of {@link Functions}, and is not intended
 * to be used outside of a stream pipeline and should not be directly instantiated by clients.
 *
 * @see Functions#pair(Function, Function)
 * @see Functions#extract(BiFunction)
 *
 * @param <T>
 * @param <U>
 */
public final class Pair<T, U> {

    final T first;
    final U second;

    Pair(T first, U second) {
        this.first = first;
        this.second = second;
    }
}
