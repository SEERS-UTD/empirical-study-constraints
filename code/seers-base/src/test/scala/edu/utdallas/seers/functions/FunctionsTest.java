package edu.utdallas.seers.functions;

import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

public class FunctionsTest {

    @Test
    public void testGroupPairs() {
        int numberOfKeys = 5;

        // A map in which each key i should have i elements, with each being the number i
        Map<Integer, List<Integer>> actual = IntStream.rangeClosed(1, numberOfKeys)
                .boxed()
                .flatMap(i ->
                        IntStream.rangeClosed(1, i)
                                .mapToObj(j -> new Pair<>(i, i))
                )
                .collect(Functions.groupPairs());

        assertThat(actual).hasSize(numberOfKeys);
        assertThat(actual).allSatisfy((i, l) -> {
            assertThat(l).hasSize(i);
            assertThat(l).containsOnlyElementsOf(Collections.singletonList(i));
        });
    }
}