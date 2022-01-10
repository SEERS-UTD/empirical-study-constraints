package edu.utdallas.seers.lasso.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import edu.utdallas.seers.lasso.entity.DetectorInput;
import edu.utdallas.seers.lasso.entity.PatternEntry;
import edu.utdallas.seers.lasso.entity.PatternTruth;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Some spreadsheet inputs need to be changed. This test compares the output of the automated
 * converter to the inputs changed by hand (test-data/manually-changed-inputs.json).
 */
public class InputTest {

    public static void main(String[] args) throws IOException {
        // Manually changed inputs json
        Path manualInputsFile = Paths.get(args[0]);

        // CSV with all inputs
        Path allInputsFile = Paths.get(args[1]);

        new InputTest().testInputs(manualInputsFile, allInputsFile);
    }

    private void testInputs(Path manualInputsFile, Path allInputsFile) throws IOException {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(DetectorInput.class, new TypeAdapter<DetectorInput>() {
                    @Override
                    public void write(JsonWriter out, DetectorInput value) {
                        // Unused
                    }

                    @Override
                    public DetectorInput read(JsonReader in) throws IOException {
                        return DetectorInput.parseInput(in.nextString());
                    }
                })
                .create();

        Map<String, PatternEntry> manualInputsMap =
                Arrays.stream(gson.fromJson(Files.newBufferedReader(manualInputsFile), PatternEntry[].class))
                        .collect(Collectors.toMap(
                                pe -> pe.getSystem() + "-" + pe.getConstraintId(),
                                pe -> pe
                        ));

        List<PatternEntry> entries = new ConstraintLoader().loadConstraints(allInputsFile).collect(Collectors.toList());

        System.out.println("Total loaded patterns: " + entries.size() + "\n");

        int problems = 0;

        for (PatternEntry loadedEntry : entries) {
            String id = loadedEntry.getConstraintId();

            PatternEntry manualEntry = manualInputsMap.get(id);

            if (manualEntry == null) {
                continue;
            }

            List<PatternTruth> manualGroundTruths = Arrays.stream(manualEntry.getpTrus())
                    .sorted(Comparator.comparing(PatternTruth::getFile))
                    .collect(Collectors.toList());

            List<PatternTruth> inputGroundTruths = Arrays.stream(loadedEntry.getpTrus())
                    .sorted(Comparator.comparing(PatternTruth::getFile))
                    .collect(Collectors.toList());

            boolean equals = manualGroundTruths.equals(inputGroundTruths);

            List<DetectorInput> manualInputs = manualEntry.getInputs();
            List<DetectorInput> entryInputs = loadedEntry.getInputs();

            equals = equals && manualInputs.equals(entryInputs);

            if (!equals) {
                problems++;
                System.out.println(loadedEntry.getConstraintId());
                System.out.println(manualGroundTruths + " -- " + inputGroundTruths);
                System.out.println(manualInputs + " -- " + entryInputs);
                System.out.println("\n");
            }
        }

        System.out.println("Problems: " + problems);
    }
}
