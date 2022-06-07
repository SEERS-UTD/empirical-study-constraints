package edu.utdallas.seers.text.preprocessing;

import java.io.*;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Preprocessing {
    /**
     * Obtained from Lemur project http://www.lemurproject.org/
     *
     * @return List of stop words.
     */
    public static List<String> loadStandardStopWords() {
        InputStream stream = Objects.requireNonNull(
                Preprocessing.class.getClassLoader()
                        .getResourceAsStream("edu/utdallas/seers/text/stopwords.txt")
        );

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            return reader.lines().collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
