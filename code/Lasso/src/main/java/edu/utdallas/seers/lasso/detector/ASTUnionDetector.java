package edu.utdallas.seers.lasso.detector;

import edu.utdallas.seers.lasso.detector.ast.PatternStore;
import edu.utdallas.seers.lasso.entity.*;
import edu.utdallas.seers.lasso.entity.constants.Constant;
import edu.utdallas.seers.lasso.entity.variables.Variable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static edu.utdallas.seers.functions.Functions.extract;
import static edu.utdallas.seers.functions.Functions.pair;

/**
 * Combines patterns detected in AST with patterns found using slicing.
 */
public abstract class ASTUnionDetector {
    protected List<Pattern> combinePatterns(List<Pattern> slicingPatterns, PatternStore patternStore,
                                            Constant<?> constant, Variable variable) {
        Map<PatternSingleLineFormat, NameValueASTPattern> astPatterns =
                patternStore.lookUpInstances(getPatternType(), constant, variable).stream()
                        /* Some slicing patterns may refer to a single line of a multi-line-pattern
                         *  Extract each line of each pattern so that we can find them individually
                         * They are uniquely filtered at the end to avoid returning duplicates */
                        .flatMap(p -> p.getLines().stream()
                                .map(pair(
                                        l -> new PatternSingleLineFormat(p.getFileName(), l, true, getPatternType()),
                                        l -> p))
                        )
                        .collect(Collectors.toMap(
                                extract((f, p) -> f),
                                extract((f, p) -> p)
                        ));

        for (Pattern slicingPattern : slicingPatterns) {
            Optional<PatternSingleLineFormat> maybe = slicingPattern.toSingleLineFormat();
            if (!maybe.isPresent()) {
                continue;
            }

            PatternSingleLineFormat key = maybe.get();
            if (!astPatterns.containsKey(key)) {
                astPatterns.put(key, new NameValueASTPattern(getPatternType(),
                        key.getFile(),
                        Collections.singleton(key.getLineNum()),
                        constant,
                        variable));
            }
        }

        return astPatterns.values().stream()
                .distinct()
                .map(NoStatementPattern::new)
                .collect(Collectors.toList());
    }

    protected abstract PatternType getPatternType();


}
