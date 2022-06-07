package edu.utdallas.seers.lasso.detector.matcher;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static com.github.javaparser.StaticJavaParser.parseExpression;

public class BooleanPropertyMatcherTest extends PatternMatcherTest {
    public static final Path TEST_FILE =
            Paths.get("programs/sample/src/main/java/sample/type/resolution/BooleanProperty.java");

    private final BooleanPropertyMatcher matcher = new BooleanPropertyMatcher();

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Override
    protected Object[] parametersForTestPositive() {
        // Cases to test the guess functionality
        List<? extends Expression> guessCases = Arrays.asList(
                r("something").findFirst(NameExpr.class).get(),
                r("this.something").findFirst(FieldAccessExpr.class).get(),
                r("something()").findFirst(MethodCallExpr.class).get(),
                r("something(1)").findFirst(MethodCallExpr.class).get(),
                r("(((something)))").findFirst(NameExpr.class).get(),
                r("(((this.something)))").findFirst(FieldAccessExpr.class).get(),
                r("(((something())))").findFirst(MethodCallExpr.class).get(),
                r("!something").findFirst(NameExpr.class).get(),
                r("x(!(something))").findFirst(NameExpr.class).get(),
                r("a || b").findFirst(NameExpr.class).get()
        );

        List<Node> resolutionCases = new TestCaseParser().parseTestCases(TEST_FILE, "P");

        return Stream.concat(resolutionCases.stream(), guessCases.stream())
                .toArray(Object[]::new);
    }

    @Override
    protected Object[] parametersForTestNegative() {
        List<Object> guessCases = Arrays.asList(
                parseExpression("-something"),
                parseExpression("4"),
                parseExpression("\"string\"")
        );

        List<Node> resolutionCases = new TestCaseParser().parseTestCases(TEST_FILE, "N");

        return Stream.concat(resolutionCases.stream(), guessCases.stream())
                .toArray(Object[]::new);
    }

    @Override
    protected PatternMatcher getMatcher() {
        return matcher;
    }
}