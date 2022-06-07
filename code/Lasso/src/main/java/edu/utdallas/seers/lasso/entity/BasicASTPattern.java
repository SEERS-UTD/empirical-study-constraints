package edu.utdallas.seers.lasso.entity;

import edu.utdallas.seers.lasso.detector.ast.PatternStore;

import java.util.Objects;
import java.util.Set;

public class BasicASTPattern extends ASTPattern {

    private final String fileName;
    private final Set<Integer> lines;

    public BasicASTPattern(PatternType patternType, String fileName, Set<Integer> lines) {
        super(patternType);
        this.fileName = fileName;
        this.lines = lines;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BasicASTPattern that = (BasicASTPattern) o;
        return fileName.equals(that.fileName) &&
                lines.equals(that.lines);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileName, lines);
    }

    @Override
    public void accept(PatternStore store) {
        store.addPattern(this);
    }

    public Set<Integer> getLines() {
        return lines;
    }

    public String getFileName() {
        return fileName;
    }
}
