package edu.utdallas.seers.lasso.entity.variables;

public class LocalVariable extends Variable {

    public LocalVariable(String qualifier, String methodName, String variableName) {
        super(Type.LOCAL_VARIABLE, qualifier + "#" + methodName + "#" + variableName);
    }
}
