package edu.utdallas.seers.lasso.entity;

import edu.utdallas.seers.lasso.detector.ast.PatternStore;
import edu.utdallas.seers.lasso.entity.constants.Constant;
import edu.utdallas.seers.lasso.entity.variables.Variable;

import java.util.Objects;
import java.util.Set;

/**
 * Has a value and a name, which can be a field name, method name, variable name...
 */
public class NameValueASTPattern extends ValueASTPattern {

    private final Variable variable;

    public NameValueASTPattern(PatternType patternType, String fileName, Set<Integer> lines,
                               Constant<?> constant, Variable variable) {
        super(patternType, fileName, lines, constant);
        this.variable = variable;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        NameValueASTPattern that = (NameValueASTPattern) o;
        return variable.equals(that.variable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), variable);
    }

    @Override
    public void accept(PatternStore store) {
        store.addPattern(this);
    }

    public Variable getVariable() {
        return variable;
    }
}
