package edu.utdallas.seers.lasso.detector.ast;

import com.github.javaparser.Position;
import com.github.javaparser.Range;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.UnknownType;
import edu.utdallas.seers.lasso.detector.*;
import edu.utdallas.seers.lasso.entity.PatternEntry;
import edu.utdallas.seers.lasso.entity.PatternSingleLineFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static edu.utdallas.seers.functions.Functions.groupPairs;
import static edu.utdallas.seers.functions.Functions.pair;

public class ASTAnalyzer {

    private static final Set<String> SOURCE_ROOT_NAMES = new HashSet<>(Arrays.asList(
            "src", "main", "java", "java-deprecated", "sources", "toolsrc"
    ));

    private static final Set<Path> EXCLUDED_SOURCE_ROOT_PATHS = Collections.singleton(
            Paths.get("android", "guava", "src")
    );

    private static final Map<PatternEntry.PatternType, NodeFilter> NODE_FILTERS = new HashMap<>();

    static {
        // TODO allow caching of matching result for nodes
        NodeFilter all = l -> true;
        Predicate<Node> ifStatement = n -> n instanceof IfStmt;
        // FIXME centralize location of implemented detectors and move this logic to subclasses of pattern matcher
        NODE_FILTERS.put(PatternEntry.PatternType.ASSIGN_CONSTANT, oneMatches(n -> n instanceof AssignExpr));
        NODE_FILTERS.put(PatternEntry.PatternType.BINARY_COMPARISON, oneMatches(BinaryComparisonMatcher::match));
        NODE_FILTERS.put(PatternEntry.PatternType.BINARY_FLAG_CHECK, oneMatches(BinaryFlagCheckMatcher::match));
        NODE_FILTERS.put(PatternEntry.PatternType.BOOLEAN_PROPERTY, oneMatches(BooleanPropertyMatcher::match));
        NODE_FILTERS.put(PatternEntry.PatternType.CONSTANT_ARGUMENT, oneMatches(ConstantArgumentMatcher::match));
        NODE_FILTERS.put(PatternEntry.PatternType.IF_CHAIN, oneMatches(ifStatement));
        // TODO adjust pattern type into the variations according to the AST
        NODE_FILTERS.put(PatternEntry.PatternType.NULL_EMPTY_CHECK, oneMatches(NullEmptyCheckDetector::match));
        // FIXME these variations are not returned by the detector right now
        NODE_FILTERS.put(PatternEntry.PatternType.NULL_CHECK, all);
        NODE_FILTERS.put(PatternEntry.PatternType.NULL_BOOLEAN_CHECK, all);
        NODE_FILTERS.put(PatternEntry.PatternType.NULL_ZERO_CHECK, all);
        NODE_FILTERS.put(PatternEntry.PatternType.REGEX, oneMatches(methodNameMatcher("matches")));
        NODE_FILTERS.put(PatternEntry.PatternType.STR_STARTS, oneMatches(methodNameMatcher("startsWith")));
        NODE_FILTERS.put(PatternEntry.PatternType.EQUALS_OR_CHAIN, oneMatches(ifStatement));
    }

    private static Predicate<Node> methodNameMatcher(String methodName) {
        return n -> n instanceof MethodCallExpr &&
                ((MethodCallExpr) n).getName().toString().equals(methodName);
    }

    private static NodeFilter oneMatches(Predicate<Node> predicate) {
        return l -> findMatch(l, predicate);
    }

    private static boolean findMatch(List<Node> nodes, Predicate<Node> predicate) {
        boolean initialMatch = nodes.stream().anyMatch(predicate);
        if (initialMatch) {
            return true;
        }

        /* If we cannot find it, it might be the case that we have a multi-line statement and the
         * detector is pointing to a different line, e.g.
         * if (unrelated &&
         *   <actual pattern>) {
         *
         * and the detector points at the first line of the if condition.*/
        Set<Node> checkedNodes = new HashSet<>(nodes);

        /* TODO instead of selecting only expressions, let detectors declare the types of
         *  nodes where they might apply */
        return checkedNodes.stream()
                .flatMap(n -> {
                    if (n instanceof Expression) {
                        return Stream.of(n);
                    }

                    if (n instanceof FieldDeclaration) {
                        return n.stream().filter(c -> c instanceof Expression);
                    }

                    Statement statement = n instanceof Statement ? ((Statement) n) : null;

                    if (statement == null) return Stream.empty();

                    // We cannot fit the for nicely in the following return
                    if (statement.isForStmt()) {
                        ForStmt forStatement = statement.asForStmt();

                        Stream<Expression> compareStream = forStatement.getCompare()
                                .map(Stream::of)
                                .orElse(Stream.empty());

                        return Stream.of(
                                compareStream,
                                forStatement.getInitialization().stream(),
                                forStatement.getUpdate().stream()
                        )
                                .flatMap(Function.identity());
                    }


                    /* TODO this misses certain expressions, e.g., a method declaration can have
                     *  annotations which can contain expressions
                     * TODO must check that detector result points to the condition of if/for etc
                     *  as opposed to the body. In the latter case don't check children. Use token
                     *  range instead of line */
                    return Stream.of(
                            statement.toAssertStmt().map(AssertStmt::getCheck),
                            statement.toDoStmt().map(DoStmt::getCondition),
                            statement.toExpressionStmt().map(ExpressionStmt::getExpression),
                            statement.toForEachStmt().map(ForEachStmt::getIterable),
                            statement.toIfStmt().map(IfStmt::getCondition),
                            // Expression in return statement is optional
                            statement.toReturnStmt().map(ReturnStmt::getExpression).flatMap(e->e),
                            statement.toWhileStmt().map(WhileStmt::getCondition)
                    )
                            .filter(Optional::isPresent)
                            .map(Optional::get);
                })
                /* Go through all children to avoid missing anything due to nested expression such
                 * as EnclosedExpr nodes. Since we are not iterating on blocks, we know we will not
                 * get to unrelated expressions. */
                .flatMap(n -> {
                    if (n instanceof Expression) {
                        return n.stream().filter(c -> c instanceof Expression);
                    } else {
                        return Stream.empty();
                    }
                })
                .filter(n -> !checkedNodes.contains(n))
                .distinct()
                .anyMatch(predicate);
    }

    private final List<Path> sourceRoots;
    private final Map<String, ASTByLine> astCache = new HashMap<>();
    final Logger logger = LoggerFactory.getLogger(ASTAnalyzer.class);

    private ASTAnalyzer(List<Path> sourceRoots) {
        this.sourceRoots = sourceRoots;
    }

    public static ASTAnalyzer create(Path sourcesDir, String systemName) {
        // todo combine source code and compiled data and add version to system names
        if (systemName.equals("httpcore")) {
            systemName = "httpcomponents";
        }

        Stream<Path> list;
        try {
            list = Files.list(sourcesDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        String finalSystemName = systemName;
        Path sourcesPath = list.filter(p -> p.getFileName().toString().toLowerCase().equals(finalSystemName) && Files.isDirectory(p))
                .map(p -> p.resolve("sources"))
                .filter(p -> Files.exists(p) && Files.isDirectory(p))
                .findAny()
                // TODO make extract source zips a gradle task and make the zip naming and contents consistent between projects
                .orElseThrow(() -> new RuntimeException(String.format(
                        "Sources for system %s were not found. Please make sure that all sources " +
                                "have been extracted (script: data/extract-sources)", finalSystemName))
                );

        Stream<Path> directories;
        try {
            directories = Files.find(sourcesPath, Integer.MAX_VALUE, (p, a) -> Files.isDirectory(p));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        /* TODO there might be a smarter way to do this, e.g. ArgoUML/sources/src/argouml-app/src
            both src are in selected but only the lower level one is valid */
        List<Path> sourceRoots = directories.filter(p -> SOURCE_ROOT_NAMES.contains(p.getFileName().toString()) &&
                EXCLUDED_SOURCE_ROOT_PATHS.stream().noneMatch(p::endsWith))
                .collect(Collectors.toList());

        if (sourceRoots.isEmpty()) {
            throw new RuntimeException("No source roots found for system " + systemName);
        }

        return new ASTAnalyzer(sourceRoots);
    }

    /**
     * Returns {@code true} if at least one expression consistent with the pattern of the result is
     * found in the AST.
     *
     * @param detectorResult Containing the line and pattern.
     * @return {@code true} if it is possible for this line to contain a result of this type.
     */
    public boolean canBeInSource(PatternSingleLineFormat detectorResult) {
        String relativePath = detectorResult.getFile();

        // FIXME deal with error
        ASTByLine ast;
        try {
            ast = findAST(relativePath);
        } catch (Exception e) {
            logger.error(e.getMessage());
            return true;
        }

        return NODE_FILTERS.get(detectorResult.getpType())
                .test(ast.getNodesForLine(detectorResult.getLineNum()));
    }

    private ASTByLine findAST(String relativePath) {
        if (astCache.containsKey(relativePath)) {
            return astCache.get(relativePath);
        }

        List<Path> candidateFiles = sourceRoots.stream()
                .map(p -> p.resolve(relativePath))
                .filter(p -> Files.exists(p))
                .collect(Collectors.toList());

        if (candidateFiles.size() > 1) {
            throw new RuntimeException(String.format("Multiple candidates for file %s: %s",
                    relativePath, candidateFiles));
        }

        Path file;

        if (candidateFiles.isEmpty()) {
            file = findPackagePrivateClass(relativePath);
        } else {
            file = candidateFiles.get(0);
        }

        try {
            ASTByLine ast;
            ast = ASTByLine.from(StaticJavaParser.parse(file));
            astCache.put(relativePath, ast);

            return ast;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Find classes that are not defined in their own file.
     *
     * @param relativePath of the class.
     * @return A file containing the class.
     */
    private Path findPackagePrivateClass(String relativePath) {
        String className = relativePath.substring(
                relativePath.lastIndexOf('/') + 1,
                relativePath.lastIndexOf('.')
        );

        List<Path> candidateFiles = sourceRoots.stream()
                .map(p -> p.resolve(relativePath).getParent())
                .filter(Files::exists)
                // For each directory that corresponds to the class package
                .flatMap(d -> {
                    Stream<Path> list;
                    try {
                        list = Files.list(d);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }

                    // For every java file in the package directory
                    return list.filter(f -> Files.isRegularFile(f) && f.toString().endsWith(".java"))
                            .filter(f -> {
                                CompilationUnit unit;
                                try {
                                    unit = StaticJavaParser.parse(f);
                                } catch (IOException e) {
                                    throw new UncheckedIOException(e);
                                }

                                return unit.getTypes().stream()
                                        // If any of the top level types matches the class name
                                        .anyMatch(t -> t.getName().toString().equals(className));
                            });
                })
                .collect(Collectors.toList());

        if (candidateFiles.isEmpty()) {
            throw new RuntimeException("Class not found: " + relativePath);
        }

        if (candidateFiles.size() > 1) {
            throw new RuntimeException(String.format("Multiple candidates for package-private class %s: %s",
                    relativePath, candidateFiles));
        }

        return candidateFiles.get(0);
    }

    private static class ASTByLine {
        static final Logger logger = LoggerFactory.getLogger(ASTByLine.class);
        private final Map<Integer, List<Node>> lineIndex;

        public ASTByLine(Map<Integer, List<Node>> lineIndex) {
            this.lineIndex = lineIndex;
        }

        public static ASTByLine from(CompilationUnit unit) {
            Map<Integer, List<Node>> lineIndex = unit.stream()
                    /* Exclude compilation units, types, and methods because they cover many lines
                     * and they don't help discriminate any patterns
                     * TODO do this by letting each detector declare which nodes it can appear in */
                    .filter(n -> !(n instanceof CompilationUnit) && !(n instanceof TypeDeclaration) &&
                            !(n instanceof CallableDeclaration) &&
                            // These nodes are useless
                            !(n instanceof UnknownType))
                    .flatMap(n -> {
                        Optional<Range> maybeRange = n.getRange();
                        if (!maybeRange.isPresent()) {
                            logger.warn("Node has no range: " + n);
                            return Stream.empty();
                        }

                        Range range = maybeRange.get();

                        Position begin = range.begin;
                        Position end = range.end;

                        // Add an index entry for each line that this node covers
                        return IntStream.rangeClosed(begin.line, end.line)
                                .boxed()
                                .map(pair(i -> i, i -> n));
                    })
                    .collect(groupPairs());

            return new ASTByLine(lineIndex);
        }

        public List<Node> getNodesForLine(int lineNum) {
            return lineIndex.get(lineNum);
        }
    }

    /**
     * Just an alias for tidier types.
     */
    private interface NodeFilter extends Predicate<List<Node>> {
    }
}
