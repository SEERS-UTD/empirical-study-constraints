package edu.utdallas.seers.lasso.entity;

import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Stream;

public class PatternOutputFormat {

    public String constraintId;
    public String constraint;
    public PatternTruth[] groundTruth;
    public List<DetectorInput> inputs;
    public String pattern;
    public String system;

    @Deprecated
    public int true_positive_number;
    public List<PatternSingleLineFormat> truePositives;

    @Deprecated
    public int fasle_positive_number;
    public List<PatternSingleLineFormat> falsePositives;

    @Deprecated
    public int false_negative_number;
    public List<PatternSingleLineFormat> false_negatives;

    // FIXME everything here assumes there is only 1 ground truth, even if it consists of multiple lines
    public PatternOutputFormat(
            PatternEntry pe,
            List<PatternSingleLineFormat> truePositives,
            List<PatternSingleLineFormat> false_posivites,
            List<PatternSingleLineFormat> falseNegatives) {
        this.constraintId = pe.getConstraintId();
        this.groundTruth = pe.getpTrus();
        this.inputs = pe.getInputs();
        this.pattern = pe.getpType().toInputString();
        this.system = pe.getSystem();
        this.constraint = pe.getcType().toInputString();

        this.truePositives = truePositives;
        this.true_positive_number = truePositives.isEmpty() ? 0 : 1;

        this.falsePositives = false_posivites;
        this.fasle_positive_number = false_posivites.size();

        this.false_negatives = falseNegatives;
        this.false_negative_number = falseNegatives.isEmpty() ? 0 : 1;
    }

    public Stream<DetectorResult> toDetectorResults() {
        BiFunction<PatternSingleLineFormat, Boolean, DetectorResult> mapper =
                (r, gt) -> new DetectorResult(
                        system,
                        constraintId,
                        constraint,
                        r,
                        gt);

        Stream<DetectorResult> truePositives = this.truePositives.stream()
                .map(tp -> mapper.apply(tp, true));
        Stream<DetectorResult> falsePositives = this.falsePositives.stream()
                .map(fp -> mapper.apply(fp, false));

        return Stream.concat(truePositives, falsePositives);
    }

    public ResultEvaluation toResultEvaluation() {
        return new ResultEvaluation(system, constraintId, constraint,
                truePositives.isEmpty() ? 0 : 1,
                falsePositives.size()
        );
    }

    /**
     * Encapsulates a single detector result.
     */
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    public static class DetectorResult {

        private final String system;
        private final String constraintID;
        private final String constraintType;
        private final String fileLines;
        private final boolean isGroundTruth;
        private final String patternType;

        DetectorResult(String system, String constraintID, String constraintType, PatternSingleLineFormat result, boolean isGroundTruth) {
            this.system = system;
            this.constraintID = constraintID;
            this.constraintType = constraintType;
            this.fileLines = result.getFile() + ":" + result.getLineNum();
            patternType = result.getpType().toInputString();
            this.isGroundTruth = isGroundTruth;
        }
    }

    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    public static class ResultEvaluation {

        private final String system;
        private final String constraintId;
        private final String pattern;
        private final int truePositives;
        private final int falsePositives;
        private final int totalResults;
        private final float precision;
        private final float recall;

        public ResultEvaluation(String system, String constraintId, String pattern, int truePositives, int falsePositives) {
            this.system = system;
            this.constraintId = constraintId;
            this.pattern = pattern;
            this.truePositives = truePositives;
            this.falsePositives = falsePositives;
            totalResults = truePositives + falsePositives;
            precision = totalResults > 0 ? ((float) truePositives) / totalResults : 0.0f;
            recall = truePositives > 0 ? 1f : 0f;
        }
    }
}
