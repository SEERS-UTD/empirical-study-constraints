package edu.utdallas.seers.lasso.detector.matcher;

import com.github.javaparser.ast.expr.BinaryExpr;
import edu.utdallas.seers.lasso.entity.ASTPattern;
import edu.utdallas.seers.lasso.entity.PatternType;

import java.util.*;

public class BinaryFlagCheckMatcher extends PatternMatcher {
    private final Set<BinaryExpr.Operator> VALID_OPERATORS = new HashSet<>(Arrays.asList(
            BinaryExpr.Operator.BINARY_AND, BinaryExpr.Operator.BINARY_OR, BinaryExpr.Operator.XOR
    ));

    @Override
    public List<ASTPattern> visit(BinaryExpr n, String arg) {
        return makeReturnPattern(n, arg, () -> VALID_OPERATORS.contains(n.getOperator()));
    }

    @Override
    public PatternType getPatternType() {
        return PatternType.BINARY_FLAG_CHECK;
    }
}
