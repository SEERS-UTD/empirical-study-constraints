package edu.utdallas.seers.lasso.detector.matcher;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import edu.utdallas.seers.lasso.entity.PatternType;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class NullEmptyCheckMatcher extends AugmentedNullCheckMatcher {

    /**
     * Checks that the expression is a comparison with the empty string.
     *
     * @param expression Expression to check.
     * @return If the expression is checking an expression equivalent to operand against the empty string.
     */
    @Override
    protected Optional<? extends Expression> extractOtherOperand(Expression expression) {
        Optional<MethodCallExpr> methodCallExpr;

        if (expression.isMethodCallExpr()) {
            methodCallExpr = Optional.of(expression.asMethodCallExpr());
        } else {
            methodCallExpr = expression.toUnaryExpr()
                    .filter(u -> u.getOperator().equals(UnaryExpr.Operator.LOGICAL_COMPLEMENT))
                    .flatMap(u -> u.getExpression().toMethodCallExpr());
        }

        Optional<Expression> maybeScope = methodCallExpr
                .filter(m -> m.getName().toString().equals("equals") &&
                        m.getArguments().size() == 1)
                .flatMap(MethodCallExpr::getScope);

        if (!maybeScope.isPresent()) {
            return Optional.empty();
        }

        List<Expression> operands = Arrays.asList(maybeScope.get(), methodCallExpr.get().getArguments().get(0));

        Optional<Expression> emptyStringOperand = operands.stream()
                .filter(e -> e.isStringLiteralExpr() && e.asStringLiteralExpr().getValue().equals(""))
                .findFirst();

        return emptyStringOperand
                .map(e -> operands.get(0).equals(e) ? operands.get(1) : operands.get(0));
    }

    @Override
    public PatternType getPatternType() {
        return PatternType.NULL_EMPTY_CHECK;
    }
}
