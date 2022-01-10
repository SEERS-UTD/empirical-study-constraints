package edu.utdallas.seers.files.csv;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Reader that encapsulates underlying file system reader management to unburden clients.
 *
 * @param <T> Class should be marked with opencsv's annotations, e.g.
 *            {@link com.opencsv.bean.CsvBindByName}
 */
public class CSVReader<T> implements Closeable {

    private final CsvToBean<T> parser;
    private final Reader reader;

    private CSVReader(Reader reader, Class<T> type) {
        this.reader = reader;
        parser = new CsvToBeanBuilder<T>(reader)
                .withType(type)
                .build();
    }

    public static <T> CSVReader<T> create(Path source, Class<T> rowType) throws IOException {
        BufferedReader reader = Files.newBufferedReader(source);

        return new CSVReader<>(reader, rowType);
    }

    public Stream<T> readAllRows() {
        return parser.stream();
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}
