package edu.utdallas.seers.lasso.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PatternSingleLineFormat {

    private String file;
    private int lineNum;
    private PatternEntry.PatternType pType;

    public PatternSingleLineFormat(String className, int lineNumber, boolean isFile, PatternEntry.PatternType pType) {
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

    public static PatternMultiLineFormat[] toMultiLineFormat(
            List<PatternSingleLineFormat> singleLinePatterns) {
        HashMap<String, List<Integer>> hm = new LinkedHashMap<>();
        for (PatternSingleLineFormat p : singleLinePatterns) {
            if (!hm.containsKey(p.getFile())) {
                List<Integer> tmp = new ArrayList<>();
                tmp.add(p.getLineNum());
                hm.put(p.getFile(), tmp);
            } else {
                hm.get(p.getFile()).add(p.getLineNum());
            }
        }
        List<PatternMultiLineFormat> plist = new ArrayList<>();
        for (String file : hm.keySet()) {
            plist.add(new PatternMultiLineFormat(file, hm.get(file)));
        }
        return plist.toArray(new PatternMultiLineFormat[0]);
    }

    public boolean match(PatternTruth groundTruth) {
        String fName = groundTruth.getFile();
        if (!fName.equals(file)) return false;
        int[] lines = groundTruth.getLines();
        for (int i : lines) {
            if (lineNum == i) return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("%s, %s : %d", pType, file, lineNum);
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }

        if (!(o instanceof PatternSingleLineFormat)) {
            return false;
        }

        // typecast o to Complex so that we can compare data members
        PatternSingleLineFormat p = (PatternSingleLineFormat) o;
        return this.file.equals(p.getFile()) && this.lineNum == p.getLineNum();
    }

    public PatternEntry.PatternType getpType() {
        return pType;
    }

    public String getFile() {
        return file;
    }

    public int getLineNum() {
        return lineNum;
    }
}
