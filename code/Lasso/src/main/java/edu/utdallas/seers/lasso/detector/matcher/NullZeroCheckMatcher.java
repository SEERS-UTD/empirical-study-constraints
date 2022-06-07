package edu.utdallas.seers.lasso.detector.matcher;

import com.github.javaparser.ast.expr.*;
import edu.utdallas.seers.lasso.entity.PatternType;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class NullZeroCheckMatcher extends AugmentedNullCheckMatcher {
    @Override
    protected Optional<? extends Expression> extractOtherOperand(Expression expression) {
        Optional<BinaryExpr> maybeBinaryExpression = expression.toBinaryExpr()
                .filter(b -> b.getOperator().equals(BinaryExpr.Operator.EQUALS) ||
                        b.getOperator().equals(BinaryExpr.Operator.NOT_EQUALS));

        if (!maybeBinaryExpression.isPresent()) {
            return Optional.empty();
        }

        BinaryExpr binaryExpr = maybeBinaryExpression.get();
        List<Expression> operands = Arrays.asList(binaryExpr.getLeft(), binaryExpr.getRight());

        Optional<Expression> nonZeroOperand = operands.stream()
                .filter(e1 -> e1.toIntegerLiteralExpr()
                        .map(l -> l.asNumber().equals(0))
                        .orElse(false)
                )
                .findFirst()
                .map(e -> operands.get(0).equals(e) ? operands.get(1) : operands.get(0));

        return nonZeroOperand.flatMap(o -> o.accept(new ScopeExtractor(), null));
    }

    @Override
    public PatternType getPatternType() {
        return PatternType.NULL_ZERO_CHECK;
    }
}
