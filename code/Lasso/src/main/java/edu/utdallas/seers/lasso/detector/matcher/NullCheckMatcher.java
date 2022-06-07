package edu.utdallas.seers.lasso.detector.matcher;

import com.github.javaparser.ast.expr.BinaryExpr;
import edu.utdallas.seers.lasso.entity.ASTPattern;
import edu.utdallas.seers.lasso.entity.PatternType;

import java.util.List;

/**
 * TODO does not accept a.equals(null)
 */
public class NullCheckMatcher extends PatternMatcher {
    @Override
    public List<ASTPattern> visit(BinaryExpr n, String arg) {
        return makeReturnPattern(n, arg, () -> isNullCheck(n));
    }

    boolean isNullCheck(BinaryExpr n) {
        return (n.getOperator().equals(BinaryExpr.Operator.EQUALS) ||
                n.getOperator().equals(BinaryExpr.Operator.NOT_EQUALS)) &&
                (n.getLeft().isNullLiteralExpr() || n.getRight().isNullLiteralExpr());
    }

    @Override
    public PatternType getPatternType() {
        return PatternType.NULL_CHECK;
    }
}
