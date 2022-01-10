package edu.utdallas.seers.lasso.detector;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.BinaryExpr;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class BinaryFlagCheckMatcher implements PatternMatcher {
    private static final Set<BinaryExpr.Operator> VALID_OPERATORS = new HashSet<>(Arrays.asList(
            BinaryExpr.Operator.BINARY_AND, BinaryExpr.Operator.BINARY_OR, BinaryExpr.Operator.XOR
    ));

    public static boolean match(Node node) {
        return node instanceof BinaryExpr &&
                VALID_OPERATORS.contains(((BinaryExpr) node).getOperator());
    }
}
