package edu.utdallas.seers.lasso.detector.matcher;

import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import edu.utdallas.seers.lasso.entity.ASTPattern;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Base class for matchers of the type null-*-check.
 */
abstract class AugmentedNullCheckMatcher extends PatternMatcher {
    private final NullCheckMatcher nullCheckMatcher = new NullCheckMatcher();
    private final ExpressionComparer expressionComparer = new ExpressionComparer();

    /**
     * Tries to find the operand in the expression next to the null check that must match the one
     * used for the null check for this pattern to apply.
     *
     * @param expression Expression to check.
     * @return The operand in the expression that conforms to the pattern, or empty if no match.
     */
    protected abstract Optional<? extends Expression> extractOtherOperand(Expression expression);

    @Override
    public List<ASTPattern> visit(BinaryExpr n, String arg) {
        if (!n.getOperator().equals(BinaryExpr.Operator.OR) &&
                !n.getOperator().equals(BinaryExpr.Operator.AND)) {
            return Collections.emptyList();
        }

        // Null check must always be the left operation
        if (!n.getLeft().isBinaryExpr()) {
            return Collections.emptyList();
        }

        BinaryExpr nullCheck = n.getLeft().asBinaryExpr();
        if (!nullCheckMatcher.isNullCheck(nullCheck)) {
            return Collections.emptyList();
        }

        // Find the non-null operand
        Expression nonNullOperand = nullCheck.getLeft().isNullLiteralExpr() ?
                nullCheck.getRight() : nullCheck.getLeft();

        return extractOtherOperand(n.getRight())
                .filter(o -> expressionComparer.areSame(nonNullOperand, o))
                .map(oo -> makePattern(n, arg))
                .map(Collections::singletonList)
                .orElse(Collections.emptyList());
    }
}
