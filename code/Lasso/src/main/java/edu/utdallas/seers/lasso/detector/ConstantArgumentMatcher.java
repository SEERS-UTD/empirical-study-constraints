package edu.utdallas.seers.lasso.detector;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;

import java.util.function.Predicate;

public class ConstantArgumentMatcher implements PatternMatcher {
    public static boolean match(Node node) {
        Expression expression = node instanceof Expression ? ((Expression) node) : null;
        if (expression == null) {
            return false;
        }

        NodeList<Expression> arguments;
        if (expression.isMethodCallExpr()) {
            arguments = expression.asMethodCallExpr().getArguments();
        } else if (expression.isObjectCreationExpr()) {
            arguments = expression.asObjectCreationExpr().getArguments();
        } else {
            return false;
        }

        Predicate<Expression> isConstant = e -> e instanceof LiteralExpr || e instanceof NameExpr || e instanceof FieldAccessExpr;

        return arguments.stream()
                // Either it's "constant" or is a unary expression of a "constant" (-1, !something)
                .anyMatch(e -> isConstant.test(e) ||
                        (e instanceof UnaryExpr && isConstant.test(((UnaryExpr) e).getExpression())));
    }
}
