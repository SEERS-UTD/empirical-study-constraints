package edu.utdallas.seers.lasso.entity;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class PatternMultiLineFormat {

    public String file;
    public int[] lines;

    public PatternMultiLineFormat(String file, int[] lines) {
        this.file = file;
        this.lines = lines;
    }

    public PatternMultiLineFormat(String file, List<Integer> lines) {
        this.file = file;
        this.lines = lines.stream().mapToInt(i -> i).toArray();
    }

    @Override
    public String toString() {
        return file + ":" +
                Arrays.stream(lines)
                        .boxed()
                        .map(String::valueOf)
                        .collect(Collectors.joining(","));
    }

    public int getNum() {
        return lines.length;
    }
}
