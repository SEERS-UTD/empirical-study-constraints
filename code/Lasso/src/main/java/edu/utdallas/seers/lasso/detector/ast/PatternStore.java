package edu.utdallas.seers.lasso.detector.ast;

import com.google.gson.TypeAdapter;
import edu.utdallas.seers.json.AdapterSupplier;
import edu.utdallas.seers.json.JSON;
import edu.utdallas.seers.json.JSONSerializable;
import edu.utdallas.seers.json.ToStringTypeAdapter;
import edu.utdallas.seers.lasso.entity.*;
import edu.utdallas.seers.lasso.entity.constants.Constant;
import edu.utdallas.seers.lasso.entity.variables.Variable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PatternStore implements JSONSerializable<PatternStore.Supplier> {

    private static final Logger logger = LoggerFactory.getLogger(PatternStore.class);
    /**
     * Used for reference in the JSON file.
     */
    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private final String projectName;

    private final Map<BasicPatternRecord, BasicASTPattern> records = new HashMap<>();
    private final Map<ValuePatternRecord, List<ValueASTPattern>> valueASTPatterns = new HashMap<>();
    private final Map<NameValuePatternRecord, List<NameValueASTPattern>> nameValuePatterns = new HashMap<>();

    public PatternStore(String projectName) {
        this.projectName = projectName;
    }

    /**
     * Creates a new store by parsing the system source or by loading a previously cached instance.
     *
     * @param sourcesDir  Path with sources for all systems.
     * @param projectName Name of the system.
     * @param cachePath   Path where stores will be cached after being constructed.
     * @return A pattern store for the system.
     */
    public static PatternStore create(Path sourcesDir, String projectName, Path cachePath) {
        Path storePath = cachePath.resolve(projectName + ".json");

        // Return cached store if it exists
        if (Files.exists(storePath)) {
            logger.info(String.format("[%s] Loading cached store from %s",
                    projectName, storePath));
            return JSON.readJSON(storePath, PatternStore.class, new Supplier());
        }

        logger.info(String.format("[%s] Running AST detector", projectName));

        // todo combine source code and compiled data and add version to system names
        if (projectName.equals("httpcore")) {
            projectName = "httpcomponents";
        }

        Path systemPath = sourcesDir.resolve(Paths.get(projectName, "sources"));

        // TODO make extract source zips a gradle task and make the zip naming and contents consistent between projects
        if (!Files.exists(systemPath) || !Files.isDirectory(systemPath)) {
            throw new RuntimeException(String.format(
                    "Sources for system %s were not found. Please make sure that all sources " +
                            "have been extracted (script: data/extract-sources)", projectName));
        }

        Set<Path> excludedPaths;
        try (Stream<String> lines = Files.lines(systemPath.getParent().resolve("exclude.txt"))) {
            excludedPaths = lines
                    .map(Paths::get)
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        ASTPatternDetector astPatternDetector = new ASTPatternDetector();
        List<? extends ASTPattern> patterns = astPatternDetector.detectPatterns(projectName, systemPath, excludedPaths);

        PatternStore store = new PatternStore(projectName);

        store.addPatterns(patterns);

        // Cache the store
        logger.info(String.format("[%s] Caching pattern store at %s",
                projectName, storePath));
        JSON.writeJSON(store, storePath, false, new Supplier());

        return store;
    }

    private void addPatterns(List<? extends ASTPattern> patterns) {
        for (ASTPattern pattern : patterns) {
            pattern.accept(this);
        }
    }

    public void addPattern(BasicASTPattern pattern) {
        records.putAll(pattern.getLines().stream()
                .map(l -> new BasicPatternRecord(pattern.getPatternType(), pattern.getFileName(), l))
                .collect(Collectors.toMap(
                        k -> k,
                        k -> pattern
                )));
    }

    public void addPattern(ValueASTPattern pattern) {
        // TODO we wouldn't need these here if the detectors were of 3 kinds: IR + check, slice + AST, IR + AST
        //  We're only doing this because we are checking ALL pattern types against records.
        //  Slice + AST and IR + AST patterns don't need to check records
        addPattern((BasicASTPattern) pattern);

        List<ValueASTPattern> list = valueASTPatterns.computeIfAbsent(
                new ValuePatternRecord(pattern),
                v -> new ArrayList<>()
        );

        list.add(pattern);
    }

    public void addPattern(NameValueASTPattern pattern) {
        addPattern((BasicASTPattern) pattern);

        List<NameValueASTPattern> list = nameValuePatterns.computeIfAbsent(
                new NameValuePatternRecord(pattern),
                v -> new ArrayList<>()
        );

        list.add(pattern);
    }

    public boolean contains(Pattern pattern) {
        Optional<PatternSingleLineFormat> patternLine = pattern.toSingleLineFormat();

        return patternLine.map(l -> records.containsKey(new BasicPatternRecord(
                l.getpType(),
                l.getFile(),
                l.getLineNum()
        )))
                .orElse(false);
    }

    public Optional<BasicASTPattern> lookUpInstance(Pattern pattern) {
        Optional<PatternSingleLineFormat> lineFormat = pattern.toSingleLineFormat();
        return lineFormat.map(l -> records.get(new BasicPatternRecord(
                l.getpType(),
                l.getFile(),
                l.getLineNum()
        )));
    }

    public List<ValueASTPattern> lookUpInstances(PatternType patternType, Constant<?> value) {
        return valueASTPatterns
                .getOrDefault(new ValuePatternRecord(patternType, value), Collections.emptyList());
    }

    public List<NameValueASTPattern> lookUpInstances(PatternType patternType, Constant<?> value, Variable name) {
        return nameValuePatterns
                .getOrDefault(new NameValuePatternRecord(patternType, value, name), Collections.emptyList());
    }

    private static class ValuePatternRecord {
        protected static final String SEPARATOR = ";;";

        private final PatternType patternType;
        private final Constant<?> constant;

        public ValuePatternRecord(ValueASTPattern pattern) {
            patternType = pattern.getPatternType();
            constant = pattern.getConstant();
        }

        public ValuePatternRecord(String string) {
            String[] split = string.split(SEPARATOR, 2);
            patternType = PatternType.valueOf(split[0]);
            constant = Constant.fromString(split[1]);
        }

        public ValuePatternRecord(PatternType patternType, Constant<?> constant) {
            this.patternType = patternType;
            this.constant = constant;
        }

        @Override
        public String toString() {
            return patternType + SEPARATOR + constant;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ValuePatternRecord that = (ValuePatternRecord) o;
            return patternType == that.patternType &&
                    constant.equals(that.constant);
        }

        @Override
        public int hashCode() {
            return Objects.hash(patternType, constant);
        }
    }

    private static class NameValuePatternRecord {

        protected static final String SEPARATOR = ";;";

        private final PatternType patternType;
        private final Constant<?> constant;
        private final Variable variable;

        public NameValuePatternRecord(NameValueASTPattern pattern) {
            patternType = pattern.getPatternType();
            constant = pattern.getConstant();
            variable = pattern.getVariable();
        }

        public NameValuePatternRecord(String string) {
            String[] split = string.split(SEPARATOR, 3);
            patternType = PatternType.valueOf(split[0]);
            variable = Variable.fromString(split[1]);
            constant = Constant.fromString(split[2]);
        }

        public NameValuePatternRecord(PatternType patternType, Constant<?> constant, Variable variable) {
            this.patternType = patternType;
            this.constant = constant;
            this.variable = variable;
        }

        @Override
        public String toString() {
            // Constant has to go at the end because empty string constant causes trouble
            return String.join(SEPARATOR,
                    patternType.toString(), variable.toString(), constant.toString());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            NameValuePatternRecord that = (NameValuePatternRecord) o;
            return patternType == that.patternType &&
                    constant.equals(that.constant) &&
                    variable.equals(that.variable);
        }

        @Override
        public int hashCode() {
            return Objects.hash(patternType, constant, variable);
        }
    }

    // TODO merge this class with PatternSingleLineFormat
    private static class BasicPatternRecord {

        private static final String SEPARATOR = ";";
        private final PatternType patternType;
        private final String file;
        private final int line;

        public BasicPatternRecord(PatternType patternType, String file, int line) {
            this.patternType = patternType;
            this.file = file;
            this.line = line;
        }

        public BasicPatternRecord(String string) {
            String[] split = string.split(SEPARATOR);
            patternType = PatternType.valueOf(split[0]);
            file = split[1];
            line = Integer.parseInt(split[2]);
        }

        @Override
        public String toString() {
            return String.join(SEPARATOR, patternType.toString(), file, String.valueOf(line));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BasicPatternRecord that = (BasicPatternRecord) o;
            return line == that.line &&
                    patternType == that.patternType &&
                    file.equals(that.file);
        }

        @Override
        public int hashCode() {
            return Objects.hash(patternType, file, line);
        }
    }

    public static class Supplier implements AdapterSupplier {
        @Override
        public Map<Class<?>, TypeAdapter<?>> getTypeAdapters() {
            HashMap<Class<?>, TypeAdapter<?>> adapters = new HashMap<>();

            adapters.put(ValuePatternRecord.class, new ToStringTypeAdapter<ValuePatternRecord>() {
                @Override
                protected ValuePatternRecord fromString(String string) {
                    return new ValuePatternRecord(string);
                }
            });

            adapters.put(NameValuePatternRecord.class, new ToStringTypeAdapter<NameValuePatternRecord>() {
                @Override
                protected NameValuePatternRecord fromString(String string) {
                    return new NameValuePatternRecord(string);
                }
            });

            adapters.put(BasicPatternRecord.class, new ToStringTypeAdapter<BasicPatternRecord>() {
                @Override
                protected BasicPatternRecord fromString(String string) {
                    return new BasicPatternRecord(string);
                }
            });

            return adapters;
        }

        @Override
        public Map<Class<?>, TypeAdapter<?>> getTypeHierarchyAdapters() {
            Map<Class<?>, TypeAdapter<?>> adapters = new HashMap<>();

            adapters.put(Constant.class, new ToStringTypeAdapter<Constant<?>>() {
                @Override
                protected Constant<?> fromString(String string) {
                    return Constant.fromString(string);
                }
            });

            adapters.put(Variable.class, new ToStringTypeAdapter<Variable>() {
                @Override
                protected Variable fromString(String string) {
                    return Variable.fromString(string);
                }
            });

            return adapters;
        }
    }
}
