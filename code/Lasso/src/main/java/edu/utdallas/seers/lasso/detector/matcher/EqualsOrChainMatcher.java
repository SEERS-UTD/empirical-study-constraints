package edu.utdallas.seers.lasso.detector.matcher;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.printer.PrettyPrinter;
import edu.utdallas.seers.lasso.entity.ASTPattern;
import edu.utdallas.seers.lasso.entity.PatternType;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * TODO converts nodes into strings to determine if there is a common element in all equalities
 * in the chain. This might not be exhaustive enough.
 */
public class EqualsOrChainMatcher extends PatternMatcher {
    /**
     * Using this printer instead of Node.toString because that method can yield different results
     * if configuration changes.
     */
    private final PrettyPrinter stringConverter = new PrettyPrinter();

    private boolean checkChain(BinaryExpr expression) {
        Optional<List<Equality>> maybeEqualities = unravelChain(expression);

        if (!maybeEqualities.isPresent()) {
            return false;
        }

        List<Equality> equalities = maybeEqualities.get();
        Set<String> accum = equalities.get(0).toSet(stringConverter);

        for (Equality current : equalities.subList(1, equalities.size())) {
            accum.retainAll(current.toSet(stringConverter));
            if (accum.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    /**
     * Each component of the chain must be a binary expression where one side is an equality and
     * the other is either (1) a {@link BinaryExpr} using the {@code ||} operator or (2) another
     * equality. This method unravels the chain if it is valid.
     * <p>
     * TODO: this would not work for expressions like (a == 0 || a == 1) || (a == 2 || a == 3)
     *
     * @param expression Expression to unravel.
     * @return a list of at least 2 equality expressions or {@link Optional#empty()} if the chain
     * is not valid.
     */
    private Optional<List<Equality>> unravelChain(BinaryExpr expression) {
        Optional<List<Equality>> equalities = unravelChainLink(expression);

        /* Since a chain must contain at least 2 equalities and a single equality is a valid input
        to unravel chain link */
        if (equalities.isPresent() && equalities.get().size() == 1) {
            return Optional.empty();
        }

        return equalities;
    }

    private Optional<List<Equality>> unravelChainLink(Expression expression) {
        Optional<Equality> equality = Equality.extract(expression);

        if (equality.isPresent()) {
            return Optional.of(Collections.singletonList(equality.get()));
        }

        BinaryExpr binaryExpr = expression.isBinaryExpr() ? expression.asBinaryExpr() : null;

        if (binaryExpr == null ||
                !binaryExpr.getOperator().equals(BinaryExpr.Operator.OR)) {
            return Optional.empty();
        }

        Optional<List<Equality>> left = unravelChainLink(binaryExpr.getLeft());
        Optional<List<Equality>> right = unravelChainLink(binaryExpr.getRight());

        if (!left.isPresent() || !right.isPresent()) {
            return Optional.empty();
        }

        return Optional.of(
                Stream.concat(left.get().stream(), right.get().stream())
                        .collect(Collectors.toList())
        );
    }

    /**
     * Only accept a candidate if it is the largest possible chain, in order to make the matching
     * unambiguous.
     *
     * @param expression Expression to test.
     * @return true if the expression is the largest possible {@link BinaryExpr} chain.
     */
    private boolean isLargest(Expression expression) {
        Node parentNode = expression.getParentNode()
                .orElseThrow(() -> new IllegalStateException("This node should have a parent"));

        Expression parent = parentNode instanceof Expression ? ((Expression) parentNode) : null;

        if (parent == null) {
            return true;
        }

        if (parent.isEnclosedExpr()) {
            return isLargest(parent);
        }

        return !parent.isBinaryExpr();
    }

    @Override
    public List<ASTPattern> visit(BinaryExpr n, String arg) {
        return makeReturnPattern(n, arg, () -> isLargest(n) && checkChain(n));
    }

    @Override
    public PatternType getPatternType() {
        return PatternType.EQUALS_OR_CHAIN;
    }

    static class Equality {

        private final Expression first;
        private final Expression second;

        private Equality(Expression first, Expression second) {
            this.first = first;
            this.second = second;
        }

        /**
         * Extracts an equality from an expression if the expression is a valid equality.
         * Accepts a == b and a.equals(b).
         *
         * @param expression Expression to extract from.
         * @return An equality object, if the expression is valid.
         */
        static Optional<Equality> extract(Expression expression) {
            if (expression.isBinaryExpr()) {
                BinaryExpr binaryExpr = expression.asBinaryExpr();
                if (binaryExpr.getOperator().equals(BinaryExpr.Operator.EQUALS)) {
                    return Optional.of(new Equality(binaryExpr.getLeft(), binaryExpr.getRight()));
                }
            }

            if (expression.isMethodCallExpr()) {
                MethodCallExpr methodCallExpr = expression.asMethodCallExpr();
                // Accepting methods like "equalsIgnoreCase" as long as there is only one argument
                if (methodCallExpr.getName().toString().startsWith("equals") &&
                        methodCallExpr.getArguments().size() == 1) {
                    return methodCallExpr.getScope()
                            .map(s -> new Equality(s, methodCallExpr.getArgument(0)));
                }
            }

            return Optional.empty();
        }

        /**
         * Turns both sides of the equality into strings and returns them as a set.
         *
         * @param stringConverter To convert nodes into strings.
         * @return A set of two strings.
         */
        Set<String> toSet(PrettyPrinter stringConverter) {
            return Stream.of(first, second)
                    .map(stringConverter::print)
                    .collect(Collectors.toSet());
        }
    }

}
