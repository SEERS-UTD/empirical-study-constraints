package edu.utdallas.seers.lasso.entity;

import com.opencsv.bean.AbstractCsvConverter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

public class PatternTruth {

    private final String file;
    private final int[] lines;

    public PatternTruth(String file, int[] lines) {
        this.file = file;
        this.lines = lines;
    }

    private static PatternTruth fromString(String input) {
        String[] split = input.split(":");
        return new PatternTruth(
                split[0],
                Arrays.stream(split[1].split(","))
                        .flatMapToInt(s -> {
                            // Some of the lines will be ranges, e.g. 100-200
                            String[] boundSplit = s.split("-");
                            int lowerBound = Integer.parseInt(boundSplit[0]);

                            if (boundSplit.length > 1) {
                                int upperBound = Integer.parseInt(boundSplit[1]);

                                return IntStream.rangeClosed(lowerBound, upperBound);
                            }

                            return IntStream.of(lowerBound);
                        })
                        .sorted()
                        .toArray()
        );
    }

    List<PatternSingleLineFormat> toSingleLineFormat() {
        List<PatternSingleLineFormat> ret = new ArrayList<>();
        for (int l : lines) {
            ret.add(new PatternSingleLineFormat(file, l, true, null));
        }
        return ret;
    }

    @Override
    public String toString() {
        return "PatternTruth{" +
                "file='" + file + '\'' +
                ", lines=" + Arrays.toString(lines) +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PatternTruth that = (PatternTruth) o;
        return file.equals(that.file) &&
                Arrays.equals(lines, that.lines);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(file);
        result = 31 * result + Arrays.hashCode(lines);
        return result;
    }

    public String getFile() {
        return file;
    }

    public int[] getLines() {
        return lines;
    }

    public static class CsvConverter extends AbstractCsvConverter {
        @Override
        public Object convertToRead(String value) {
            return fromString(value);
        }
    }

}
