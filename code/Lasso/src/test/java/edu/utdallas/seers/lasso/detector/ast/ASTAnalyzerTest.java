package edu.utdallas.seers.lasso.detector.ast;

import com.opencsv.bean.CsvBindByName;
import edu.utdallas.seers.files.csv.CSVReader;
import edu.utdallas.seers.files.csv.CSVWriter;
import edu.utdallas.seers.lasso.entity.PatternEntry;
import edu.utdallas.seers.lasso.entity.PatternSingleLineFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static edu.utdallas.seers.functions.Functions.groupPairs;
import static edu.utdallas.seers.functions.Functions.pair;

public class ASTAnalyzerTest {
    final Logger logger = LoggerFactory.getLogger(ASTAnalyzerTest.class);

    public static void main(String[] args) throws IOException {
        Path allResultsPath = Paths.get(args[0]);
        Path truePositivesPath = Paths.get(args[1]);
        Path sourcesDir = Paths.get(args[2]);
        Path outputDir = Paths.get(args[3]);

        new ASTAnalyzerTest().test(allResultsPath, truePositivesPath, sourcesDir, outputDir);
    }

    private void test(Path allResultsPath, Path truePositivesPath, Path sourcesDir, Path outputDir) throws IOException {
        Map<String, List<DetectorResult>> allResults = loadResultsFile(allResultsPath);
        Map<String, List<DetectorResult>> truePositives = loadTruePositives(truePositivesPath, allResults);

        int accBefore = 0, accAfter = 0, accTPBefore = 0, accTPAfter = 0;

        Files.createDirectories(outputDir);

        CSVWriter<OutputRow> summaryWriter = CSVWriter.create(outputDir.resolve("summary.csv"));
        CSVWriter<DetectorResult> allResultsWriter = CSVWriter.create(outputDir.resolve("all-results.csv"));

        for (String system : allResults.keySet()) {
            logger.info("System " + system);
            List<DetectorResult> systemResults = fixSystemResults(allResults, system);
            List<DetectorResult> systemTruePositives = truePositives.getOrDefault(system, Collections.emptyList());

            OutputRow row = testSystem(system, systemResults, systemTruePositives, sourcesDir, allResultsWriter);
            accBefore += row.resultsBefore;
            accAfter += row.resultsAfter;
            accTPBefore += row.tpBefore;
            accTPAfter += row.tpAfter;

            summaryWriter.writeRows(Stream.of(row));
        }

        summaryWriter.writeRows(Stream.of(new OutputRow("TOTAL", accBefore, accAfter, accTPBefore, accTPAfter)));

        summaryWriter.close();
        allResultsWriter.close();
    }

    // FIXME in detector, line with wrong pattern is being marked as ground truth
    private List<DetectorResult> fixSystemResults(Map<String, List<DetectorResult>> allResults, String system) {
        List<DetectorResult> rawSystemResults = allResults.get(system);

        Map<String, List<String>> groundTruthFileLines = rawSystemResults.stream()
                .filter(r -> r.isGroundTruth)
                .map(pair(r -> r.constraintID, r -> r.fileLines))
                .collect(groupPairs());

        return rawSystemResults.stream()
                .peek(r -> {
                    String fileLines = r.fileLines;

                    if (!r.isGroundTruth &&
                            groundTruthFileLines.getOrDefault(r.constraintID, Collections.emptyList())
                                    .contains(fileLines)) {
                        logger.warn("Marking result as ground truth: " + r);
                        r.isGroundTruth = true;
                    }
                })
                .collect(Collectors.toList());
    }

    private OutputRow testSystem(String system, List<DetectorResult> originalResults,
                                 List<DetectorResult> truePositives, Path sourcesDir, CSVWriter<DetectorResult> allResultsWriter) {
        ASTAnalyzer analyzer = ASTAnalyzer.create(sourcesDir, system);

        List<DetectorResult> filteredResults = originalResults.stream()
                .filter(r -> {
                    String[] split = r.fileLines.split(":");
                    PatternEntry.PatternType type = PatternEntry.PatternType.fromString(r.patternType);
                    PatternSingleLineFormat format =
                            new PatternSingleLineFormat(split[0], Integer.parseInt(split[1]), true, type);

                    r.possiblyInAST = analyzer.canBeInSource(format);

                    if (!r.possiblyInAST && r.isGroundTruth) {
                        logger.warn("Lost ground truth: " + r);
                    }

                    return r.possiblyInAST;
                })
                .collect(Collectors.toList());

        // All possiblyInAST should have been set
        allResultsWriter.writeRows(originalResults.stream());

        List<DetectorResult> lostTruePositives = findLostTruePositives(truePositives, filteredResults);

        lostTruePositives.forEach(r -> logger.warn("Lost true positive: " + r));

        int sizeBefore = originalResults.size();
        int sizeAfter = filteredResults.size();
        int truePositivesAfter = truePositives.size() - lostTruePositives.size();

        return new OutputRow(system, sizeBefore, sizeAfter, truePositives.size(), truePositivesAfter);
    }

    private List<DetectorResult> findLostTruePositives(List<DetectorResult> truePositives, List<DetectorResult> validResults) {
        Function<DetectorResult, String> idMapper = r -> String.join("_", Arrays.asList(r.constraintID, r.fileLines));
        Set<String> validResultIDs = validResults.stream()
                .map(idMapper)
                .collect(Collectors.toSet());

        return truePositives.stream()
                .filter(r -> !validResultIDs.contains(idMapper.apply(r)))
                .collect(Collectors.toList());
    }

    private Map<String, List<DetectorResult>> loadTruePositives(Path truePositivesPath, Map<String, List<DetectorResult>> allResults) throws IOException {
        Map<String, List<DetectorResult>> groundTruths = allResults.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream().filter(r -> r.isGroundTruth).collect(Collectors.toList())
                ));

        return loadResultsFile(truePositivesPath).entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> Stream.concat(
                                e.getValue().stream(),
                                groundTruths.getOrDefault(e.getKey(), Collections.emptyList()).stream()
                        ).collect(Collectors.toList())
                ));
    }

    private Map<String, List<DetectorResult>> loadResultsFile(Path allResultsPath) throws IOException {
        CSVReader<DetectorResult> reader = CSVReader.create(allResultsPath, DetectorResult.class);

        return reader.readAllRows()
                .collect(Collectors.groupingBy(r -> r.system));
    }

    public static class OutputRow {

        @CsvBindByName(column = "System")
        private final String system;
        @CsvBindByName(column = "Results Before")
        private final int resultsBefore;
        @CsvBindByName(column = "Amount reduced")
        private final int amountReduced;
        @CsvBindByName(column = "Results after")
        private final int resultsAfter;
        @CsvBindByName(column = "% Reduced")
        private final float percentageReduced;
        @CsvBindByName(column = "TP before")
        private final int tpBefore;
        @CsvBindByName(column = "TP after")
        private final int tpAfter;
        @CsvBindByName(column = "TP reduced")
        private final int tpReduced;
        @CsvBindByName(column = "% TP reduced")
        private final float percentageTPReduced;

        public OutputRow(String system, int resultsBefore, int resultsAfter, int truePositivesBefore, int truePositivesAfter) {
            this.system = system;
            this.resultsBefore = resultsBefore;
            this.resultsAfter = resultsAfter;
            amountReduced = resultsBefore - resultsAfter;
            percentageReduced = (float) amountReduced / resultsBefore;

            this.tpBefore = truePositivesBefore;
            this.tpAfter = truePositivesAfter;
            tpReduced = truePositivesBefore - truePositivesAfter;
            percentageTPReduced = truePositivesBefore == 0 ? 0 : (float) tpReduced / truePositivesBefore;
        }
    }

    // Duplicates class in POF but helps avoid having to put annotations there
    public static class DetectorResult {

        @CsvBindByName(column = "SYSTEM")
        String system;
        @CsvBindByName(column = "CONSTRAINTID")
        String constraintID;
        @CsvBindByName(column = "CONSTRAINTTYPE")
        String constraintType;
        @CsvBindByName(column = "FILELINES")
        String fileLines;
        @CsvBindByName(column = "ISGROUNDTRUTH")
        boolean isGroundTruth;
        @CsvBindByName(column = "PATTERNTYPE")
        String patternType;
        @CsvBindByName(column = "Possibly in AST")
        boolean possiblyInAST;

        @Override
        public String toString() {
            return "DetectorResult{" +
                    "constraintID='" + constraintID + '\'' +
                    ", fileLines='" + fileLines + '\'' +
                    ", patternType='" + patternType + '\'' +
                    '}';
        }
    }
}