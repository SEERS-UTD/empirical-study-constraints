package edu.utdallas.seers.lasso.detector.ast;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PatternStoreTest {

    public static void main(String[] args) throws IOException {
        Path sourcesDir = Paths.get(args[0]);
        Path cacheDir = Paths.get(args[1]);

        new PatternStoreTest()
                .test(sourcesDir, cacheDir);
    }

    private void test(Path sourcesDir, Path cacheDir) throws IOException {
        Files.find(sourcesDir, 1, (p, a) -> !p.equals(sourcesDir) && Files.isDirectory(p))
                .map(p -> p.getFileName().toString())
                .sorted()
                .forEachOrdered(n -> PatternStore.create(sourcesDir, n, cacheDir));
    }
}