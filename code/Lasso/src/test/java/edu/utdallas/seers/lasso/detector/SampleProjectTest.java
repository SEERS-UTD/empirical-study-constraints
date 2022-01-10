package edu.utdallas.seers.lasso.detector;

import edu.utdallas.seers.lasso.experiment.PatternDetectionEvaluator;

import java.io.IOException;
import java.nio.file.Paths;

import static edu.utdallas.seers.files.Files.getTempFilePath;

/**
 * Runs all test constraints on the sample project.
 * <p>
 * NOTE: Use Lasso as working directory as opposed to its parent.
 */
public class SampleProjectTest {
    public static void main(String[] args) {
        new SampleProjectTest().testSampleConstraints();
    }

    private void testSampleConstraints() {
        new PatternDetectionEvaluator()
                .startExperiment(
                        Paths.get("test-data", "sample-traces.csv"),
                        Paths.get("programs"),
                        getTempFilePath("sample-results"),
                        getTempFilePath("sample-debug"),
                        1);
    }
}
