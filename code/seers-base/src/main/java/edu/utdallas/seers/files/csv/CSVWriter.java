package edu.utdallas.seers.files.csv;

import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Writes instances of a class to CSV, automatically inferring headers. To be used when records
 * need to be written one by one, for example, as part of a stream pipeline when each item needs
 * to be processed by multiple writers.
 * <p>
 * This class is thread-safe to allow use in parallel pipelines.
 *
 * @param <T> The type of the rows.
 */
public class CSVWriter<T> implements Closeable {

    private final BufferedWriter outputWriter;
    private final StatefulBeanToCsv<T> csvFormatWriter;

    private CSVWriter(BufferedWriter outputWriter) {
        this.outputWriter = outputWriter;
        csvFormatWriter = new StatefulBeanToCsvBuilder<T>(outputWriter).build();
    }

    public static <T> CSVWriter<T> create(Path destination) throws IOException {
        Files.createDirectories(destination.getParent());

        BufferedWriter bufferedWriter = Files.newBufferedWriter(destination);

        return new CSVWriter<>(bufferedWriter);
    }

    public synchronized void writeRows(Stream<T> rows) {
        try {
            /* This method doesn't actually write, but rather schedules the write on a separate
            thread, so it should return fast */
            csvFormatWriter.write(rows);
        } catch (CsvDataTypeMismatchException | CsvRequiredFieldEmptyException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws IOException {
        outputWriter.close();
    }
}
