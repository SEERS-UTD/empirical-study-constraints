package edu.utdallas.seers.files;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Files {
    private Files() {
    }

    /**
     * Returns the path of a file in the default temp directory of the system.
     *
     * @param first first component of the file path.
     * @param more  optional additional components of the file path.
     * @return a new Path.
     */
    public static Path getTempFilePath(String first, String... more) {
        return Paths.get(System.getProperty("java.io.tmpdir"))
                .resolve(Paths.get(first, more));
    }
}
