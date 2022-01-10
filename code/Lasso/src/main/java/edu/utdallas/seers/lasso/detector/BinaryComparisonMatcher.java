package edu.utdallas.seers.lasso.detector;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class BinaryComparisonMatcher implements PatternMatcher {
    private static final Set<BinaryExpr.Operator> VALID_OPERATORS = new HashSet<>(Arrays.asList(
            BinaryExpr.Operator.OR, BinaryExpr.Operator.AND, BinaryExpr.Operator.EQUALS,
            BinaryExpr.Operator.NOT_EQUALS, BinaryExpr.Operator.LESS, BinaryExpr.Operator.GREATER,
            BinaryExpr.Operator.LESS_EQUALS, BinaryExpr.Operator.GREATER_EQUALS
    ));

    public static boolean match(Node node) {
        if (node instanceof BinaryExpr &&
                VALID_OPERATORS.contains(((BinaryExpr) node).getOperator())) {
            return true;
        }

        if (node instanceof MethodCallExpr) {
            MethodCallExpr methodCall = (MethodCallExpr) node;

            return methodCall.getName().toString().equals("equals") &&
                    methodCall.getArguments().size() == 1;
        }

        return false;
    }
}
