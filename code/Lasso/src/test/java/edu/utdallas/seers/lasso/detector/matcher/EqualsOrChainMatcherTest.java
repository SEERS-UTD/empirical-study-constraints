package edu.utdallas.seers.lasso.detector.matcher;

import com.github.javaparser.ast.expr.BinaryExpr;

@SuppressWarnings("OptionalGetWithoutIsPresent")
public class EqualsOrChainMatcherTest extends PatternMatcherTest {

    private final EqualsOrChainMatcher matcher = new EqualsOrChainMatcher();

    @Override
    protected Object[] parametersForTestPositive() {
        return new Object[]{
                r("a == 1 || a == 2").findFirst(BinaryExpr.class).get(),
                r("a == 1 || a.equals(2)").findFirst(BinaryExpr.class).get(),
                r("a == 1 || a == 2 || 3 == a").findFirst(BinaryExpr.class).get(),
                r("a == b || a.equals(b) || 3 == b || b.equals(c)").findFirst(BinaryExpr.class).get(),
                r("a.equalsIgnoreCase(\"a\") || a.equalsIgnoreCase(\"b\")")
                        .findFirst(BinaryExpr.class).get()
        };
    }

    @Override
    protected Object[] parametersForTestNegative() {
        return new Object[]{
                r("a == 1").findFirst(BinaryExpr.class).get(),
                // Will only return true if the target is the biggest binary expression
                r("a == 1 || a == 2").findFirst(BinaryExpr.class,
                        e->e.getOperator().equals(BinaryExpr.Operator.EQUALS)).get(),
                r("a == 1 || b == 2").findFirst(BinaryExpr.class).get(),
                r("a == 1 || a.equals(2, 3)").findFirst(BinaryExpr.class).get(),
                r("a == 1 || a.myequals(2)").findFirst(BinaryExpr.class).get(),
        };
    }

    @Override
    protected PatternMatcher getMatcher() {
        return matcher;
    }
}