package edu.utdallas.seers.lasso.detector.ast;

import com.github.javaparser.*;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import edu.utdallas.seers.lasso.detector.matcher.*;
import edu.utdallas.seers.lasso.entity.ASTPattern;
import edu.utdallas.seers.lasso.entity.PatternType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class ASTPatternDetector {

    /**
     * @param projectName   Name of the project.
     * @param systemPath    Path to the source code of the system.
     * @param excludedPaths All source files under these paths will be ignored. They should be
     *                      relative to systemPath.
     * @return Map from file path to list of patterns found in that file.
     */
    List<? extends ASTPattern> detectPatterns(String projectName, Path systemPath, Set<Path> excludedPaths) {
        return DirectoryWalker.create(projectName, systemPath, excludedPaths).walk();
    }

    /**
     * Aggregates all available pattern matchers for efficient matching on the AST.
     */
    private static class AggregatedPatternMatcher {

        private final Logger logger = LoggerFactory.getLogger(AggregatedPatternMatcher.class);

        /**
         * We could compile a list of valid node types by querying each detector and this would
         * allow for discarding certain nodes quickly (e.g. CompilationUnit), however, since each
         * candidate node would have to be compared with Class.instanceOf, this would probably not
         * significantly increase efficiency. Instead, each detector should be able to quickly
         * discard invalid nodes.
         */
        private final Map<PatternType, PatternMatcher> matchers = Arrays.stream(PatternType.values())
                .collect(Collectors.toMap(
                        t -> t,
                        PatternType::getMatcher
                ));

        public List<ASTPattern> match(CompilationUnit unit, String fileName) {
            return unit.stream()
                    .flatMap(n -> matchers.values().stream()
                            .flatMap(m -> {
                                try {
                                    return n.accept(m, fileName).stream();
                                } catch (Exception | Error e) {
                                    // TODO deal with these errors
//                                    logger.warn("Error processing \"{}\" with {} in {}, {}",
//                                            n,
//                                            m.getPatternType(),
//                                            unit.getStorage()
//                                                    .map(s -> s.getPath().toString())
//                                                    .orElse("<No file>"),
//                                            e.getMessage()
//                                    );

                                    return Stream.empty();
                                }
                            })
                    )
                    .collect(Collectors.toList());
        }
    }

    /**
     * Using walker because {@link Files#walk(Path, FileVisitOption...)} doesn't allow skipping
     * subtrees.
     */
    private static class DirectoryWalker extends SimpleFileVisitor<Path> {

        private static final Set<String> SOURCE_ROOT_NAMES = new HashSet<>(Arrays.asList(
                "src", "main", "java", "java-deprecated", "sources", "toolsrc"
        ));

        final Logger logger = LoggerFactory.getLogger(DirectoryWalker.class);
        private final Set<Path> excludedPaths;
        private final AggregatedPatternMatcher matcher = new AggregatedPatternMatcher();
        private final List<ASTPattern> collection = new ArrayList<>();
        private final Path projectPath;
        private final JavaParser parser;
        private int counter = 0;
        private final String projectName;

        private DirectoryWalker(String projectName, Path projectPath, Set<Path> excludedPaths, JavaParser parser) {
            this.projectName = projectName;
            this.projectPath = projectPath;
            this.excludedPaths = excludedPaths;
            this.parser = parser;
        }

        static DirectoryWalker create(String projectName, Path projectPath, Set<Path> excludedPaths) {
            Stream<Path> directories;
            try {
                directories = Files.find(projectPath, Integer.MAX_VALUE, (p, a) -> Files.isDirectory(p));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            List<TypeSolver> javaParserSolvers;
            if (projectPath.toString().contains("jedit")) {
                // jEdit puts sources at the top level
                // TODO standardize process of finding source folders for all projects
                javaParserSolvers = Collections.singletonList(
                        new JavaParserTypeSolver(projectPath.resolve("jedit-runnable")));
            } else {
            /* TODO there might be a smarter way to do this, e.g. ArgoUML/sources/src/argouml-app/src
                both src are in selected but only the lower level one is valid */
                // Find all source dirs and create solvers for type resolution within the project
                javaParserSolvers = directories.filter(p -> SOURCE_ROOT_NAMES.contains(p.getFileName().toString()) &&
                        excludedPaths.stream().noneMatch(p::endsWith))
                        .map(JavaParserTypeSolver::new)
                        .collect(Collectors.toList());
            }

            if (javaParserSolvers.isEmpty()) {
                throw new RuntimeException("No source roots found for project " + projectPath);
            }

            List<TypeSolver> typeSolvers = new ArrayList<>(javaParserSolvers);
            // Add JRE solver
            typeSolvers.add(0, new ReflectionTypeSolver(true));

            // TODO missing type resolution from libraries. Could replace src resolution with the project jar + dependency jars
            CombinedTypeSolver solver = new CombinedTypeSolver(typeSolvers.toArray(new TypeSolver[0]));
            JavaParser parser = new JavaParser(new ParserConfiguration()
                    .setSymbolResolver(new JavaSymbolSolver(solver))
            );

            return new DirectoryWalker(projectName, projectPath, excludedPaths, parser);
        }

        List<? extends ASTPattern> walk() {
            try {
                Files.walkFileTree(
                        projectPath,
                        // Follow links
                        EnumSet.allOf(FileVisitOption.class),
                        Integer.MAX_VALUE,
                        this
                );
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            return collection;
        }

        private String extractFullyQualifiedName(CompilationUnit compilationUnit) {
            String fileName = compilationUnit.getStorage().map(CompilationUnit.Storage::getFileName)
                    .orElseThrow(() -> new IllegalArgumentException("Compilation unit has no file name: " + compilationUnit));

            return compilationUnit.getPackageDeclaration()
                    .map(d -> d.getName().toString().replace('.', '/') + "/" + fileName)
                    .orElse(fileName);
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            FileVisitResult returnValue = FileVisitResult.CONTINUE;

            if (!file.toString().endsWith(".java")) {
                return returnValue;
            }

            ParseResult<CompilationUnit> result = parser.parse(file);

            if (!result.getResult().isPresent()) {
                logger.warn("Invalid Java file: " + file);
                return returnValue;
            }

            CompilationUnit compilationUnit = result.getResult().get();

            String fileName = extractFullyQualifiedName(compilationUnit);
            collection.addAll(matcher.match(compilationUnit, fileName));

            if (++counter % 200 == 0) {
                logger.info(String.format("[%s] Processed %d Java files...",
                        projectName, counter));
            }

            return returnValue;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            // Files.walk does not allow skipping subtrees
            if (excludedPaths.contains(projectPath.relativize(dir))) {
                logger.info("Skipping directory for AST pattern search: " + dir);
                return FileVisitResult.SKIP_SUBTREE;
            }

            return FileVisitResult.CONTINUE;
        }
    }
}
