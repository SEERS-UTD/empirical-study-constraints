package edu.utdallas.seers.lasso.entity.variables;

public class Field extends Variable {
    public Field(String qualifier, String name) {
        super(Type.FIELD, qualifier + "#" + name);
    }

}
