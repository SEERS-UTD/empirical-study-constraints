package edu.utdallas.seers.text.preprocessing;

import edu.utdallas.seers.testing.TestUtils;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static edu.utdallas.seers.testing.TestUtils.a;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(JUnitParamsRunner.class)
public class TextPreprocessorTest {

    private TextPreprocessor textPreprocessor;

    @Before
    public void setUp() {
        textPreprocessor = new TextPreprocessor();
    }

    @Test
    @Parameters
    public void testTokenize(String string, String... expected) {
        assertThat(textPreprocessor.tokenize(string))
                .as("Tokenize")
                .containsExactly(expected);
    }

    public Object[] parametersForTestTokenize() {
        return a(
                a("", TestUtils.<String>a()),
                a("word", a("word")),
                a("word-word don't words'", a("word", "word", "don't", "words")),
                a("CamelCase snake_case echo123", a("CamelCase", "snake_case", "echo123")),
                a("word\\\\word2++word3", a("word", "word2", "word3"))
        );
    }
}