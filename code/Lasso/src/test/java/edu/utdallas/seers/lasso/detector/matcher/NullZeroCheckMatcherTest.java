package edu.utdallas.seers.lasso.detector.matcher;

import static com.github.javaparser.StaticJavaParser.parseExpression;

public class NullZeroCheckMatcherTest extends PatternMatcherTest {

    private NullZeroCheckMatcher matcher = new NullZeroCheckMatcher();

    @Override
    protected Object[] parametersForTestPositive() {
        return new Object[]{
                parseExpression("a == null || a == 0"),
                parseExpression("a != null && a != 0"),
                parseExpression("a != null && 0 != a"),
                parseExpression("a != null && a.size() != 0"),
                parseExpression("a == null || a.size == 0"),
                parseExpression("a == null || 0 == a.size "),
                parseExpression("a.getB().getC() == null || 0 == a.getB().getC().getSize()"),
        };
    }

    @Override
    protected Object[] parametersForTestNegative() {
        return new Object[]{
                parseExpression("a == null || b == 0"),
                parseExpression("b == 0 || a == null"),
                parseExpression("a == null || a.equals(0)"),
                parseExpression("a == null || a == 1"),
                parseExpression("a == null || 1 == a.size ")
        };
    }

    @Override
    protected PatternMatcher getMatcher() {
        return matcher;
    }
}