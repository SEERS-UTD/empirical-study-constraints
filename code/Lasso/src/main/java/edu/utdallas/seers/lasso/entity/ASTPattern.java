package edu.utdallas.seers.lasso.entity;

import edu.utdallas.seers.lasso.detector.ast.PatternStore;

public abstract class ASTPattern {
    protected final PatternType patternType;

    public ASTPattern(PatternType patternType) {
        this.patternType = patternType;
    }

    // TODO decouple this into interfaces so that this class doesn't have to reference PatternStore
    abstract public void accept(PatternStore store);

    public PatternType getPatternType() {
        return patternType;
    }
}
