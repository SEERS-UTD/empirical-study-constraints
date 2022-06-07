package edu.utdallas.seers.lasso.detector;

import com.ibm.wala.ipa.slicer.Statement;
import edu.utdallas.seers.lasso.detector.ast.PatternStore;
import edu.utdallas.seers.lasso.entity.Pattern;
import edu.utdallas.seers.lasso.entity.PatternType;
import edu.utdallas.seers.lasso.entity.SimplePattern;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * TODO perhaps make the results distinct
 */
public class NullEmptyCheckDetector {

    private static final List<PatternType> PATTERNS = Arrays.asList(
            PatternType.NULL_CHECK, PatternType.NULL_EMPTY_CHECK, PatternType.NULL_ZERO_CHECK
    );

    /**
     * Finds the instances of this pattern in the slice.
     *
     * @param slice        A slice to match on.
     * @param patternStore Store to check matches on.
     * @return A list of pattern instances that were found in the slice,
     */
    List<Pattern> detectPattern(Slice slice, PatternStore patternStore) {
        return slice.getSliceStatements().stream()
                .flatMap(s -> checkStatement(s, patternStore))
                .collect(Collectors.toList());
    }

    private Stream<Pattern> checkStatement(Statement statement, PatternStore patternStore) {
        SimplePattern temp = new SimplePattern(statement, null);

        Set<PatternType> patternsPresent = PATTERNS.stream()
                .filter(p -> {
                    temp.setPatternType(p);

                    return patternStore.contains(temp);
                })
                .collect(Collectors.toSet());

        Stream<PatternType> resultStream;
        if (patternsPresent.size() == 1) {
            resultStream = patternsPresent.stream();
        } else {
            // If we have null check + other stuff, get rid of the null check because it is redundant
            resultStream = patternsPresent.stream()
                    .filter(p -> !p.equals(PatternType.NULL_CHECK));
        }

        return resultStream
                .map(t -> new SimplePattern(statement, t));
    }
}