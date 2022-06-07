package edu.utdallas.seers.lasso.entity.variables;

public class Method extends Variable {

    // TODO create factory method that creates instances for normal method or for constructor
    public static final String CONSTRUCTOR_NAME = "<init>";

    public Method(String qualifier, String name) {
        super(Type.METHOD, qualifier + "#" + name);
    }
}
