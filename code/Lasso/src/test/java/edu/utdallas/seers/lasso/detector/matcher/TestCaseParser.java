package edu.utdallas.seers.lasso.detector.matcher;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Loads test cases from Java files in the sample project. The parsed cases support type resolution.
 */
public class TestCaseParser {
    /**
     * Loads cases from TEST_FILE with the specified type: positive or negative.
     *
     * @param testFile File to load cases from.
     * @param type     {@code "P"} for positive or {@code "N"} for negative.
     * @return The test cases.
     */
    List<Node> parseTestCases(Path testFile, String type) {
        JavaParserTypeSolver source = new JavaParserTypeSolver(Paths.get("programs/sample/src/main/java"));
        ReflectionTypeSolver jre = new ReflectionTypeSolver(true);
        CombinedTypeSolver solver = new CombinedTypeSolver(source, jre);
        JavaParser parser = new JavaParser(new ParserConfiguration()
                .setSymbolResolver(new JavaSymbolSolver(solver))
        );

        ParseResult<CompilationUnit> result;
        try {
            result = parser.parse(testFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        CompilationUnit unit = result.getResult().orElseThrow(RuntimeException::new);

        Pattern pattern = Pattern.compile("\\s*" + type + "\\s*");

        return unit.stream()
                .filter(n -> n.getComment().map(c -> pattern.matcher(c.getContent()).matches()).orElse(false))
                .collect(Collectors.toList());
    }
}