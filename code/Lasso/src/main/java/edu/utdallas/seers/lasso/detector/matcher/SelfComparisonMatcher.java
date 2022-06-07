package edu.utdallas.seers.lasso.detector.matcher;

import com.github.javaparser.ast.expr.BinaryExpr;
import edu.utdallas.seers.lasso.entity.ASTPattern;
import edu.utdallas.seers.lasso.entity.PatternType;

import java.util.List;

public class SelfComparisonMatcher extends PatternMatcher {

    private final ExpressionComparer expressionComparer = new ExpressionComparer();

    @Override
    public List<ASTPattern> visit(BinaryExpr n, String arg) {
        return makeReturnPattern(n, arg,
                () -> (n.getOperator().equals(BinaryExpr.Operator.EQUALS) ||
                        n.getOperator().equals(BinaryExpr.Operator.NOT_EQUALS)) &&
                        expressionComparer.areSame(n.getLeft(), n.getRight()));
    }

    @Override
    public PatternType getPatternType() {
        return PatternType.SELF_COMPARISON;
    }
}
