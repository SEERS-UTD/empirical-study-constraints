package edu.utdallas.seers.lasso.detector.matcher;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.printer.PrettyPrinter;

/**
 * Checks that two expressions are the same by pretty printing them and then comparing the strings.
 */
class ExpressionComparer {
    private final PrettyPrinter stringConverter = new PrettyPrinter();

    boolean areSame(Expression first, Expression second) {
        return stringConverter.print(first).equals(stringConverter.print(second));
    }
}