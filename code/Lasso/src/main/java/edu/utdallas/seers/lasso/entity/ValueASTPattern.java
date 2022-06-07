package edu.utdallas.seers.lasso.entity;

import edu.utdallas.seers.lasso.detector.ast.PatternStore;
import edu.utdallas.seers.lasso.entity.constants.Constant;

import java.util.Objects;
import java.util.Set;

public class ValueASTPattern extends BasicASTPattern {

    private final Constant<?> constant;

    public ValueASTPattern(PatternType patternType, String fileName, Set<Integer> lines, Constant<?> constant) {
        super(patternType, fileName, lines);
        this.constant = constant;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        ValueASTPattern that = (ValueASTPattern) o;
        return constant.equals(that.constant);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), constant);
    }

    @Override
    public void accept(PatternStore store) {
        store.addPattern(this);
    }

    public Constant<?> getConstant() {
        return constant;
    }
}
