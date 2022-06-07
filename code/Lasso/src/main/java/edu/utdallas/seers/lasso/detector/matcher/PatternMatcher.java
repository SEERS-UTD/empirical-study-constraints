package edu.utdallas.seers.lasso.detector.matcher;

import com.github.javaparser.Range;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.visitor.GenericVisitorWithDefaults;
import edu.utdallas.seers.lasso.entity.ASTPattern;
import edu.utdallas.seers.lasso.entity.BasicASTPattern;
import edu.utdallas.seers.lasso.entity.PatternType;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

// TODO specify common traits of pattern detector implementations and move all detector code to subclasses

/**
 * Returns a pattern if the node matches the pattern. The argument is the path of the file where
 * the node appears.
 */
public abstract class PatternMatcher extends GenericVisitorWithDefaults<List<ASTPattern>, String> {
    /**
     * Subclasses may use this method to create a default entry for a pattern match.
     *
     * @param node     Node that matches the pattern.
     * @param fileName Argument passed to the visit method.
     * @return A pattern instance.
     */
    protected ASTPattern makePattern(Node node, String fileName) {
        return new BasicASTPattern(
                getPatternType(),
                fileName,
                nodeToLines(node)
        );
    }

    protected Set<Integer> nodeToLines(Node node) {
        Range range = node.getRange()
                .orElseThrow(() -> new IllegalArgumentException("Node has no range: " + node));
        return IntStream.rangeClosed(range.begin.line, range.end.line)
                .boxed().
                        collect(Collectors.toSet());
    }

    /**
     * Convenience method that returns a singleton list with the pattern if the condition holds,
     * otherwise an empty list. Exists because most detectors return a only one pattern per match.
     * @param node The node.
     * @param fileName File where the pattern is found.
     * @param condition Has to hold for a valid pattern.
     * @return Singleton or empty list.
     */
    protected List<ASTPattern> makeReturnPattern(Node node, String fileName, Supplier<Boolean> condition) {
        if (condition.get()) {
            return Collections.singletonList(makePattern(node, fileName));
        }

        return Collections.emptyList();
    }

    @Override
    public List<ASTPattern> defaultAction(Node n, String arg) {
        return Collections.emptyList();
    }

    @Override
    public List<ASTPattern> defaultAction(NodeList n, String arg) {
        return Collections.emptyList();
    }

    public abstract PatternType getPatternType();
}
