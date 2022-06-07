package edu.utdallas.seers.lasso.experiment;

import edu.utdallas.seers.files.csv.CSVWriter;
import edu.utdallas.seers.lasso.data.ConstraintLoader;
import edu.utdallas.seers.lasso.detector.PatternDetector;
import edu.utdallas.seers.lasso.detector.SystemInfo;
import edu.utdallas.seers.lasso.entity.PatternEntry;
import edu.utdallas.seers.lasso.entity.PatternOutputFormat;
import edu.utdallas.seers.parallel.Parallel;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static edu.utdallas.seers.files.Files.getTempFilePath;

public class PatternDetectionEvaluator {
    final Logger logger = LoggerFactory.getLogger(PatternDetectionEvaluator.class);
    private final Path sourcesPath;

    public PatternDetectionEvaluator(Path sourcesPath) {
        this.sourcesPath = sourcesPath;
    }

    public static void main(String[] args) {
        ArgumentParser parser = ArgumentParsers.newFor("PatternDetectionEvaluator").build()
                .defaultHelp(true);

        parser.addArgument("constraintsPath")
                .help("csv of constraints with ground truth");

        parser.addArgument("targetsPath")
                .help("Path of the target systems' binaries and entry point data");

        parser.addArgument("sourcesPath")
                .help("Path of the target systems' source code");

        parser.addArgument("destPath")
                .help("Directory where evaluation will be written");

        parser.addArgument("-c", "--cache-dir")
                .help("Cache files/debug directory")
                .setDefault(getTempFilePath("detector-cache").toString());

        parser.addArgument("-t", "--threads")
                .help("Number of threads to use. Each thread will evaluate one system, using ~4GB of heap")
                .type(Integer.class)
                .setDefault(4);

        Namespace namespace;
        try {
            namespace = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
            return;
        }

        new PatternDetectionEvaluator(Paths.get(namespace.getString("sourcesPath")))
                .startExperiment(
                        Paths.get(namespace.getString("constraintsPath")),
                        Paths.get(namespace.getString("targetsPath")),
                        Paths.get(namespace.getString("destPath")),
                        Paths.get(namespace.getString("cache_dir")),
                        namespace.getInt("threads"));
    }

    public void startExperiment(Path constraintsPath, Path targetsPath, Path destPath, Path tempDir, int threads) {
        try {
            Parallel.runWithThreads(
                    () -> runExperiment(constraintsPath, targetsPath, destPath, tempDir),
                    threads
            );
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void runExperiment(Path constraintsPath, Path targetsPath, Path destPath, Path tempDir) {
        Path allResultsPath = destPath.resolve("all-results.csv");
        Path byConstraintPath = destPath.resolve("evaluation-by-constraint.csv");

        try (Stream<List<PatternOutputFormat>> results = runDetectors(constraintsPath, targetsPath, tempDir);

             CSVWriter<PatternOutputFormat.DetectorResult> allResultsWriter =
                     CSVWriter.create(allResultsPath);

             CSVWriter<PatternOutputFormat.ResultEvaluation> evaluationWriter =
                     CSVWriter.create(byConstraintPath)
        ) {
            Files.createDirectories(tempDir);

            results.forEach(rl -> {
                evaluateResultList(rl, evaluationWriter);
                outputResultList(rl, allResultsWriter);
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void evaluateResultList(List<PatternOutputFormat> resultList, CSVWriter<PatternOutputFormat.ResultEvaluation> csvWriter) {
        csvWriter.writeRows(
                resultList.stream()
                        .map(PatternOutputFormat::toResultEvaluation)
        );
    }

    private void outputResultList(List<PatternOutputFormat> resultList, CSVWriter<PatternOutputFormat.DetectorResult> csvWriter) {
        // TODO how long it took for each result
        csvWriter.writeRows(
                resultList.stream()
                        .flatMap(PatternOutputFormat::toDetectorResults)
        );
    }

    /**
     * Uses multiple threads for execution. Run with a custom thread pool to control the amount
     * of memory required.
     *
     * @param constraintsPath File with constraints.
     * @param targetsPath     Path of the target systems.
     * @param tempDir         Directory to output debug info.
     * @return A stream of all results for all systems.
     * @throws IOException if there are errors reading.
     */
    private Stream<List<PatternOutputFormat>> runDetectors(Path constraintsPath, Path targetsPath, Path tempDir) throws IOException {
        Map<String, SystemInfo> infoMap = SystemInfo.buildInfo();

        // Load data
        List<PatternEntry> allConstraints = new ConstraintLoader()
                .loadConstraints(constraintsPath)
                .collect(Collectors.toList());

        Map<String, List<PatternEntry>> constraints = allConstraints.stream()
                .collect(Collectors.groupingBy(PatternEntry::getSystem));

        logger.info(String.format("Loaded %d constraints from %d systems",
                allConstraints.size(),
                constraints.size()
        ));

        // Collect these paths first, otherwise it won't do it in parallel
        List<Path> systemPaths = Files.walk(targetsPath, 1)
                .filter(path -> Files.isDirectory(path) && !path.equals(targetsPath))
                .collect(Collectors.toList());

        return systemPaths.parallelStream()
                .map(p -> {
                    String systemName = p.getFileName().toString();

                    List<PatternEntry> inputs = constraints.getOrDefault(systemName, Collections.emptyList());

                    if (inputs.isEmpty()) {
                        return null;
                    }

                    PatternEntry[] patternInputs = inputs.toArray(new PatternEntry[0]);

                    return PatternDetector.create(systemName, infoMap.get(systemName), true,
                            tempDir, sourcesPath)
                            .detectPatterns(patternInputs);
                })
                .filter(Objects::nonNull);
    }
}
