package edu.utdallas.seers.lasso.detector.matcher;

import com.github.javaparser.ast.Node;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class AssignConstantMatcherTest extends PatternMatcherTest {
    public static final Path TEST_FILE = Paths.get("programs/sample/src/main/java/sample/type/resolution/AssignConstant.java");
    /**
     * Matcher is stateless.
     */
    private final PatternMatcher matcher = new AssignConstantMatcher();

    @Override
    @SuppressWarnings("unused")
    protected Object[] parametersForTestPositive() {
        List<Node> cases = new TestCaseParser()
                .parseTestCases(TEST_FILE, "P");

        return cases.toArray();
    }

    @Override
    @SuppressWarnings("unused")
    protected Object[] parametersForTestNegative() {
        return new TestCaseParser()
                .parseTestCases(TEST_FILE, "N")
                .toArray();
    }

    @Override
    protected PatternMatcher getMatcher() {
        return matcher;
    }
}