package edu.utdallas.seers.lasso.detector.matcher;

import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import edu.utdallas.seers.lasso.entity.ASTPattern;
import edu.utdallas.seers.lasso.entity.PatternType;

import java.util.*;

public class BinaryComparisonMatcher extends PatternMatcher {
    private final Set<BinaryExpr.Operator> VALID_OPERATORS = new HashSet<>(Arrays.asList(
            BinaryExpr.Operator.OR, BinaryExpr.Operator.AND, BinaryExpr.Operator.EQUALS,
            BinaryExpr.Operator.NOT_EQUALS, BinaryExpr.Operator.LESS, BinaryExpr.Operator.GREATER,
            BinaryExpr.Operator.LESS_EQUALS, BinaryExpr.Operator.GREATER_EQUALS
    ));

    @Override
    public List<ASTPattern> visit(BinaryExpr n, String arg) {
        return makeReturnPattern(n, arg, () -> VALID_OPERATORS.contains(n.getOperator()));
    }

    @Override
    public List<ASTPattern> visit(MethodCallExpr n, String arg) {
        // TODO could use resolution here to make sure it is the correct equals method
        return makeReturnPattern(n, arg,
                () -> n.getName().toString().equals("equals") && n.getArguments().size() == 1);
    }

    @Override
    public PatternType getPatternType() {
        return PatternType.BINARY_COMPARISON;
    }
}
