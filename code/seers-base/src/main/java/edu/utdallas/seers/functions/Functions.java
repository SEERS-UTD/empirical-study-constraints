package edu.utdallas.seers.functions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collector;

@SuppressWarnings("unused")
public class Functions {
    private Functions() {
    }

    /**
     * Returns a function that converts elements of type {@code T} to pairs {@code (R1, R2)}.
     *
     * @param firstMapper  Mapper for first element.
     * @param secondMapper Mapper for second element.
     * @param <T>          Type of input elements.
     * @param <R1>         Type of pair first element.
     * @param <R2>         Type of pair second element.
     * @return A mapper function.
     */
    public static <T, R1, R2> Function<T, Pair<R1, R2>> pair(
            Function<T, R1> firstMapper,
            Function<T, R2> secondMapper
    ) {
        return t -> new Pair<>(firstMapper.apply(t), secondMapper.apply(t));
    }

    /**
     * Returns a function that converts pairs of type {@code (T1, T2)} to elements of {@code R}.
     *
     * @param mapper Function to convert pair elements.
     * @param <T1>   First type of pair.
     * @param <T2>   Second type of pair.
     * @param <R>    Type of result.
     * @return A mapper function.
     */
    public static <T1, T2, R> Function<Pair<T1, T2>, R> extract(BiFunction<T1, T2, R> mapper) {
        return p -> mapper.apply(p.first, p.second);
    }

    /**
     * Returns a collector that turns a stream of {@code Pair<T1, T2>} into a
     * {@code Map<T1, List<T2>>}.
     *
     * @param <T1> Type of map keys.
     * @param <T2> Type of map values.
     * @return A collector.
     */
    public static <T1, T2> Collector<Pair<T1, T2>, ?, Map<T1, List<T2>>> groupPairs() {
        // Mutably merge two lists
        BinaryOperator<List<T2>> mergeLists = (l1, l2) -> {
            l1.addAll(l2);
            return l1;
        };

        return Collector.of(
                HashMap::new,
                (m, p) -> m.computeIfAbsent(p.first, k1 -> new ArrayList<>()).add(p.second),
                // Mutably merge two maps
                (m1, m2) -> {
                    m2.forEach((k, l) -> m1.merge(k, l, mergeLists));
                    return m1;
                });
    }
}
