package edu.utdallas.seers.lasso.data;

import com.opencsv.bean.CsvBindAndSplitByName;
import com.opencsv.bean.CsvBindByName;
import edu.utdallas.seers.files.csv.CSVReader;
import edu.utdallas.seers.lasso.entity.DetectorInput;
import edu.utdallas.seers.lasso.entity.PatternEntry;
import edu.utdallas.seers.lasso.entity.PatternTruth;
import edu.utdallas.seers.lasso.entity.PatternType;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ConstraintLoader {
    private final Pattern scrDirPattern = Pattern.compile("^.*src/(main/)*(java/)*");
    private final Pattern methodParamsPattern = Pattern.compile("\\(.*\\)");

    /**
     * Assumes that a package name will never contain caps.
     */
    private final Pattern nestedClassPattern = Pattern.compile("^[a-z_.]+\\.[A-Z][A-Za-z_]*\\.([A-Z].*)");

    /**
     * Directly modify an input.
     */
    private final Map<Pair<String, String>, String> inputMods = new HashMap<>();

    // TODO implement better
    private final Set<String> implementedDetectors = Stream.of(
            PatternEntry.PatternType.BOOLEAN_PROPERTY,
            PatternEntry.PatternType.BINARY_COMPARISON,
            PatternEntry.PatternType.CONSTANT_ARGUMENT,
            PatternEntry.PatternType.NULL_CHECK,
            PatternEntry.PatternType.ASSIGN_CONSTANT,
            PatternEntry.PatternType.IF_CHAIN,
            PatternEntry.PatternType.BINARY_FLAG_CHECK,
            PatternEntry.PatternType.EQUALS_OR_CHAIN,
            PatternEntry.PatternType.SWITCH_LEN_CHAR,
            PatternEntry.PatternType.SELF_COMPARISON,
            PatternEntry.PatternType.RETURN_CONSTANT,
            PatternEntry.PatternType.NULL_ZERO_CHECK,
            PatternEntry.PatternType.NULL_EMPTY_CHECK
    )
            .map(PatternEntry.PatternType::toInputString)
            .collect(Collectors.toSet());

    {
        // fixme opencsv is detecting \n as an escaped n if the field is not quoted, which is the way google exports it
        inputMods.put(new Pair<>("RHI-3", "n"), "\n");
        inputMods.put(new Pair<>("SWA-24", "javax.swing.filechooser.FileNameExtensionFilter"), "javax.swing.filechooser.FileNameExtensionFilter#<init>");
        inputMods.put(new Pair<>("UML-3", "javax.swing#JCheckBox"), "javax.swing.JCheckBox#<init>");
        inputMods.put(new Pair<>("UML-7", "javax.swing#JCheckBox"), "javax.swing.JCheckBox#<init>");
        inputMods.put(new Pair<>("SWA-18", "gov.usgs.volcanoes.swarm.event.PickMenu#createPickMenu#weight"),
                "gov.usgs.volcanoes.swarm.event.PickMenu$2#val$weight");
        inputMods.put(new Pair<>("JTI-12", "org.joda.time.DateTime"), "org.joda.time.DateTime#<init>");
        inputMods.put(new Pair<>("JTI-1", "org.joda.time.DateTime"), "org.joda.time.DateTime#<init>");
        inputMods.put(new Pair<>("JTI-6", "org.joda.time.DateTime"), "org.joda.time.DateTime#<init>");
        inputMods.put(new Pair<>("JTI-3", "org.joda.time.Instant#Instant(long)"), "org.joda.time.Instant#<init>");
        inputMods.put(new Pair<>("HCO-7", "org.apache.http.pool.AbstractConnPool#AbstractConnPool"), "org.apache.http.pool.AbstractConnPool#<init>");
    }

    public Stream<PatternEntry> loadConstraints(Path csvFile) throws IOException {
        return CSVReader.create(csvFile, ConstraintRow.class)
                .readAllRows()
                // Only allow ones with implemented detectors
                .filter(r -> implementedDetectors.contains(r.pattern))
                .map(this::convertRow);
    }

    private PatternEntry convertRow(ConstraintRow row) {
        List<DetectorInput> inputs = Stream.of(row.input1, row.input2, row.input3)
                .filter(s -> s != null && !s.isEmpty())
                .map(input -> DetectorInput.parseInput(convertInput(input, row.id)))
                .collect(Collectors.toList());

        return new PatternEntry(
                row.id,
                convertGroundTruths(row.truths, row.id),
                inputs,
                row.system,
                PatternEntry.ConstraintType.fromString(row.constraintType),
                PatternEntry.PatternType.fromString(row.pattern)
        );
    }

    /**
     * Converts each of the inputs for the detector.
     *
     * @param input        Input string.
     * @param constraintID ID of the constraint.
     * @return Converted string.
     */
    private String convertInput(String input, String constraintID) {
        // Convert "" to actual empty string
        if (input.equals("\"\"")) {
            return "";
        }

        Pair<String, String> key = new Pair<>(constraintID, input);
        if (inputMods.containsKey(key)) {
            return inputMods.get(key);
        }

        // Method parameters to disambiguate methods are not being used
        Matcher matcher = methodParamsPattern.matcher(input);

        String replaced = input;

        if (matcher.find()) {
            matcher.reset();
            replaced = matcher.replaceFirst("");
        }

        // Can combine method parameter removal + @
        if (replaced.startsWith("!")) {
            int index = replaced.lastIndexOf("#");

            return replaced.substring(0, index) + "@" + replaced.substring(index + 1);
        }

        matcher = nestedClassPattern.matcher(replaced);

        if (matcher.find()) {
            String suffix = matcher.group(1);

            replaced = replaced.replace("." + suffix, "$" + suffix);
        }

        return replaced;
    }

    /**
     * Trims paths from 'src/' or similar, because the compiled classes don't have the full path.
     * TODO: check that these still refer to the same class, since sometimes there are duplicates in project
     * <p>
     * Also converts lines if necessary, see groundTruthMods
     *
     * @param truths       Ground truths from spreadsheet
     * @param constraintID The ID.
     * @return Converted ground truths.
     */
    private PatternTruth[] convertGroundTruths(List<PatternTruth> truths, String constraintID) {
        return truths.stream()
                .map(pt -> {
                    String file = pt.getFile();
                    String processedFile;

                    // iTrust JSPs get converted to java files and get a different path
                    if (file.startsWith("WebRoot")) {
                        // something_jsp.java
                        processedFile = file.replace("WebRoot", "org/apache/jsp")
                                .replace(".", "_")
                                .replace("-", "_002d")
                                + ".java";

                    } else {
                        Matcher matcher = scrDirPattern.matcher(file);

                        processedFile = matcher.replaceFirst("");
                    }

                    return new PatternTruth(processedFile, pt.getLines());
                })
                .toArray(PatternTruth[]::new);
    }

    /**
     * Exists to easily load rows from CSV. Must be public so that opencsv can access it.
     */
    public static class ConstraintRow {
        @CsvBindByName(column = "System")
        String system;

        @CsvBindByName(column = "ID")
        String id;
        @CsvBindAndSplitByName(column = "Enforcing Statement", splitOn = "\n",
                elementType = PatternTruth.class, collectionType = ArrayList.class,
                converter = PatternTruth.CsvConverter.class)
        List<PatternTruth> truths;
        @CsvBindByName(column = "Part 1")
        String input1;
        @CsvBindByName(column = "Part 2")
        String input2;
        @CsvBindByName(column = "Part 3")
        String input3;

        @CsvBindByName(column = "Pattern")
        String pattern;

        @CsvBindByName(column = "Type")
        String constraintType;
    }

    private static class Pair<T1, T2> {
        private final T1 left;
        private final T2 right;

        public Pair(T1 left, T2 right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Pair<?, ?> pair = (Pair<?, ?>) o;
            return left.equals(pair.left) &&
                    right.equals(pair.right);
        }

        @Override
        public String toString() {
            return "Pair{" +
                    "left=" + left +
                    ", right=" + right +
                    '}';
        }

        @Override
        public int hashCode() {
            return Objects.hash(left, right);
        }
    }

    static class GroundTruthModsMap {
        Map<Pair<String, String>, GroundTruthMod> map = new HashMap<>();

        public void put(String id, String file, String newFile, int... lineNumbers) {
            String actualNewFile = newFile != null ? newFile : file;
            Pair<String, String> key = new Pair<>(id, file);

            if (map.containsKey(key)) {
                throw new IllegalArgumentException("Key exists " + key);
            }

            map.put(key, new GroundTruthMod(actualNewFile, lineNumbers));
        }

        public Optional<GroundTruthMod> get(String id, String file) {
            return Optional.ofNullable(map.get(new Pair<>(id, file)));
        }
    }

    static class GroundTruthMod {

        private final String newFile;
        private final int[] lineNumbers;

        public GroundTruthMod(String newFile, int... lineNumbers) {
            this.newFile = newFile;
            this.lineNumbers = lineNumbers;
        }
    }
}