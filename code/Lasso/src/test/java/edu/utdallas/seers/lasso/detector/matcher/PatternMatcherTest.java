package edu.utdallas.seers.lasso.detector.matcher;

import com.github.javaparser.ast.Node;
import edu.utdallas.seers.lasso.entity.ASTPattern;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Optional;

import static com.github.javaparser.StaticJavaParser.parseBlock;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(JUnitParamsRunner.class)
public abstract class PatternMatcherTest {

    @Test
    @Parameters
    public void testPositive(Node node) {
        assertThat(node.accept(getMatcher(), null))
                .as("\"%s\" is a valid pattern instance", node)
                .isNotEmpty();
    }

    protected Node r(String expression) {
        // Expressions must have a parent for the guess methods to work, so we wrap them using these methods
        return parseBlock(String.format("{return %s;}", expression));
    }

    @SuppressWarnings("unused")
    protected abstract Object[] parametersForTestPositive();

    @Test
    @Parameters
    public void testNegative(Node node) {
        assertThat(node.accept(getMatcher(), null))
                .as("\"%s\" is NOT a valid pattern instance", node)
                .isEmpty();
    }

    @SuppressWarnings("unused")
    protected abstract Object[] parametersForTestNegative();

    protected abstract PatternMatcher getMatcher();
}
