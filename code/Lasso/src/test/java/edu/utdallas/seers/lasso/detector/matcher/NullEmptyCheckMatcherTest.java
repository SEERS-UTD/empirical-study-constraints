package edu.utdallas.seers.lasso.detector.matcher;

import static com.github.javaparser.StaticJavaParser.parseExpression;

public class NullEmptyCheckMatcherTest extends PatternMatcherTest {

    private final NullEmptyCheckMatcher matcher = new NullEmptyCheckMatcher();

    @Override
    protected Object[] parametersForTestPositive() {
        return new Object[]{
                parseExpression("a == null || a.equals(\"\")"),
                parseExpression("null == a || \"\".equals(a)"),
                parseExpression("a != null && !a.equals(\"\")"),
                parseExpression("null != a && !\"\".equals(a)"),
                parseExpression("getA() == null || getA().equals(\"\")"),
                parseExpression("getA() != null && !getA().equals(\"\")"),
                parseExpression("null == getA() || \"\".equals(getA())"),
                parseExpression("a.getS() == null || a.getS().equals(\"\")")
        };
    }

    @Override
    protected Object[] parametersForTestNegative() {
        return new Object[]{
                parseExpression("a == null || b.equals(\"\")"),
                parseExpression("a == null || a.equals(\"\", 2)"),
                parseExpression("a == null || a.is(\"\")"),
                parseExpression("a.getS() == null || b.getS().equals(\"\")")
        };
    }

    @Override
    protected PatternMatcher getMatcher() {
        return matcher;
    }
}