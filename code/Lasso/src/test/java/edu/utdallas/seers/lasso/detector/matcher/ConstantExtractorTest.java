package edu.utdallas.seers.lasso.detector.matcher;

import com.github.javaparser.ast.expr.Expression;
import edu.utdallas.seers.lasso.entity.constants.Constant;
import edu.utdallas.seers.lasso.entity.constants.IntegerConstant;
import edu.utdallas.seers.lasso.entity.constants.LongConstant;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.file.Path;
import java.nio.file.Paths;

import static com.github.javaparser.StaticJavaParser.parseExpression;
import static org.assertj.core.api.Assertions.assertThat;


@RunWith(JUnitParamsRunner.class)
public class ConstantExtractorTest {

    public static final Path TEST_FILE = Paths.get("programs/sample/src/main/java/sample/type/resolution/ConstantExtractor.java");
    private final ConstantExtractor constantExtractor = new ConstantExtractor();

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Test
    @Parameters
    public void testExtractConstant(Expression e, Constant<?> expected) {
        assertThat(constantExtractor.extractConstant(e, false).get())
                .isEqualTo(expected);
    }

    public Object[] parametersForTestExtractConstant() {
        return new Object[]{
                a(parseExpression("5"), new IntegerConstant(5)),
                a(parseExpression("(((5)))"), new IntegerConstant(5)),
                a(parseExpression("-5"), new IntegerConstant(-5)),
                a(parseExpression("2147483647"), new IntegerConstant(Integer.MAX_VALUE)),
                a(parseExpression("-2147483648"), new IntegerConstant(Integer.MIN_VALUE)),
                a(parseExpression("--5"), new IntegerConstant(4)),
                a(parseExpression("-(-((5--)))"), new IntegerConstant(4)),
                a(parseExpression("9223372036854775807L"), new LongConstant(Long.MAX_VALUE)),
                a(parseExpression("-9223372036854775808L"), new LongConstant(Long.MIN_VALUE)),
        };
    }

    @Test
    @Parameters
    public void testResolutionPositive(Expression e) {
        assertThat(constantExtractor.extractConstant(e, true))
                .isPresent();
    }

    @SuppressWarnings("unused")
    private Object[] parametersForTestResolutionPositive() {
        return new TestCaseParser().parseTestCases(TEST_FILE, "P")
                .toArray();
    }

    @Test
    @Parameters
    public void testResolutionNegative(Expression expression) {
        assertThat(constantExtractor.extractConstant(expression, true))
                .isNotPresent();
    }

    @SuppressWarnings("unused")
    private Object[] parametersForTestResolutionNegative() {
        return new TestCaseParser().parseTestCases(TEST_FILE, "N")
                .toArray();
    }

    private Object[] a(Object... params) {
        return params;
    }
}