package edu.utdallas.seers.lasso.entity;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PatternSingleLineFormat {

    private String file;
    private int lineNum;
    private PatternType pType;

    public PatternSingleLineFormat(String className, int lineNumber, boolean isFile, PatternType pType) {
        // FIXME use actual file name instead of class name, because this breaks for inner classes
        if (isFile) {
            this.file = className;
        } else {
            Pattern pattern = Pattern.compile("(?<=,L).*?(?=\u003e)");
            Matcher matcher = pattern.matcher(className);
            matcher.find();
            this.file = matcher.group(0).split("\\$")[0] + ".java";
        }
        this.lineNum = lineNumber;
        this.pType = pType;
    }

    @Override
    public String toString() {
        return String.format("%s, %s : %d", pType, file, lineNum);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PatternSingleLineFormat that = (PatternSingleLineFormat) o;
        return lineNum == that.lineNum &&
                file.equals(that.file) &&
                pType == that.pType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(file, lineNum, pType);
    }

    public PatternType getpType() {
        return pType;
    }

    public String getFile() {
        return file;
    }

    public int getLineNum() {
        return lineNum;
    }
}
