package edu.utdallas.seers.lasso.misc;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.slicer.Statement;
import edu.utdallas.seers.lasso.entity.PatternTruth;

import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Offers methods for writing debug information for detectors.
 */
public class DebugWriter {

    /**
     * How many lines before and after should we look to match a pattern truth.
     */
    public static final int LINE_TOLERANCE = 3;
    public static final OpenOption[] APPEND_OPTIONS =
            new OpenOption[]{StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.APPEND};
    public static final int SLICE_SIZE = 50;

    private final Path destinationDir;
    private final String systemName;
    private final Path irFile;
    private final Path sliceFile;
    private final Pattern lineNumberPattern = Pattern.compile("\\(line (\\d+)\\)");
    private final Path usagesFile;
    private Set<String> writtenMethods;

    public DebugWriter(String systemName, Path destinationDir) {
        this.systemName = systemName;
        this.destinationDir = destinationDir;

        irFile = destinationDir.resolve(systemName + "-ir.txt");

        sliceFile = destinationDir.resolve(systemName + "-slice.txt");

        usagesFile = destinationDir.resolve(systemName + "-usages.txt");

        // Delete files in case they exist from a previous run, since we always append to them
        try {
            Files.deleteIfExists(irFile);
            Files.deleteIfExists(sliceFile);
            Files.deleteIfExists(usagesFile);
        } catch (IOException ignore) {
        }
    }

    public void writeCallGraphClasses(CallGraph callGraph) {
        Iterator<CGNode> it = callGraph.iterator();
        Set<String> s = new HashSet<>();

        while (it.hasNext()) {
            CGNode node = it.next();
            s.add(node.getMethod().getDeclaringClass().getName().toString());
        }

        try {
            Files.write(destinationDir.resolve(systemName + "-cg.txt"), s);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void writeGroundTruthIR(String constraintId, List<PatternTruth> truths, CallGraph cg) {
        // Avoids repeating nodes when printing IR during lookup
        writtenMethods = new HashSet<>();

        // Append because this method will be called for each constraint of the system
        // Constructor deletes the file if it exists
        try {
            String s = "Constraint: " + constraintId + "\n=============================\n";
            Files.write(irFile, s.getBytes(), APPEND_OPTIONS);

            List<String> justIRs = truths.stream()
                    .flatMap(t -> findIR(t, cg).stream())
                    .collect(Collectors.toList());

            // TODO move to seers-base: concat lists
            List<String> irs = Stream.of(
                    Collections.singletonList("--- Ground truth IRs ---"),
                    justIRs,
                    Arrays.asList("--- END ground truth IRs ---", "")
            )
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList());

            Files.write(irFile, irs, APPEND_OPTIONS);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<String> findIR(PatternTruth patternTruth, CallGraph cg) {
        String file = patternTruth.getFile();
        String className = file.substring(file.lastIndexOf('/') + 1, file.lastIndexOf('.'));

        int[] lines = patternTruth.getLines();

        List<String> result = new ArrayList<>();

        /* To account for the ground truth being in an inner class
         * when used with Pattern.find allows "/className$.*" but not "/classNameAndThenSome" */
        Pattern classNamePattern = Pattern.compile("/" + className + "(\\$|$)");

        for (CGNode node : cg) {
            String nodeClassName = node.getMethod().getDeclaringClass().getName().toString();
            Matcher classNameMatcher = classNamePattern.matcher(nodeClassName);

            if (!classNameMatcher.find()) {
                continue;
            }

            String irString = node.getIR().toString();

            Matcher lineNumberMatcher = lineNumberPattern.matcher(irString);

            L:
            while (lineNumberMatcher.find()) {
                // Check if any of the lines in the IR match any of the lines in the GT given the tolerance
                for (int line : lines) {
                    int lineLow = line - LINE_TOLERANCE;
                    int lineHigh = line + LINE_TOLERANCE;

                    int irLine = Integer.parseInt(lineNumberMatcher.group(1));

                    if (lineLow <= irLine && irLine <= lineHigh) {
                        result.add(irString);
                        break L;
                    }
                }
            }
        }

        return result;
    }

    /**
     * Must guarantee that {@link DebugWriter#writeGroundTruthIR(String, List, CallGraph)} is
     * always called before this method to write the constraint number and reset the "written" set.
     *
     * @param node Whose IR will be written.
     */
    public void writeIR(CGNode node) {
        String signature = node.getMethod().getSignature();
        if (writtenMethods.contains(signature)) {
            return;
        } else {
            writtenMethods.add(signature);
        }

        try {
            String string = node.getIR().toString() + "\n";
            Files.write(irFile, string.getBytes(), APPEND_OPTIONS);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void writeSlice(Statement source, Collection<Statement> slice) {
        String string = "source: " + source.toString() + "\n" +
                "Slice [\n" +
                slice.stream()
                        .limit(SLICE_SIZE)
                        .map(s1 -> "\t" + s1.toString())
                        .collect(Collectors.joining("\n"))
                + "\n]\n\n";

        try {
            Files.write(sliceFile, string.getBytes(), APPEND_OPTIONS);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void writeSliceConstraint(String constraintId) {
        /* Slicing is being called in many places, so we need to call this method every time we
         * start a new constraint to print the name in the slices file */
        String string = "Constraint: " + constraintId + "\n";
        try {
            Files.write(sliceFile, string.getBytes(), APPEND_OPTIONS);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Write IR for all methods of the given classes.
     *
     * @param classes fully qualified class names as found in the CG.
     * @param cg      CG.
     */
    public void writeAllIR(Set<String> classes, CallGraph cg) {
        if (classes.isEmpty()) {
            return;
        }

        for (CGNode node : cg) {
            String className = node.getMethod().getDeclaringClass().getName().toString();

            if (classes.contains(className)) {
                writeIR(node);
            }
        }
    }

    public void writeAllUsages(String constraintId, Set<String> names, CallGraph cg) {
        if (names.isEmpty()) {
            return;
        }

        Predicate<String> containsHash = s -> s.contains("#");

        Set<String> noClassNames = names.stream()
                .filter(containsHash.negate())
                .collect(Collectors.toSet());

        Set<String> withClassNames = names.stream()
                .filter(containsHash)
                .map(s -> {
                    String[] split = s.split("#");

                    return "L" + split[0].replace('.', '/') + "#" + split[1];
                })
                .collect(Collectors.toSet());

        List<String> usages = StreamSupport.stream(cg.spliterator(), true)
                .filter(n -> {
                    String methodName = n.getMethod().getName().toString();

                    if (noClassNames.contains(methodName)) {
                        return true;
                    }

                    return withClassNames.contains(n.getMethod().getDeclaringClass().getName().toString() + "#" + methodName);
                })
                .map(Object::toString)
                .collect(Collectors.toList());

        try {
            Files.write(usagesFile, Collections.singletonList(constraintId), APPEND_OPTIONS);
            Files.write(usagesFile, usages, APPEND_OPTIONS);
            Files.write(usagesFile, Collections.singletonList("\n"), APPEND_OPTIONS);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
