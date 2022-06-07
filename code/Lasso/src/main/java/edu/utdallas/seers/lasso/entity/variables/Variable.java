package edu.utdallas.seers.lasso.entity.variables;

import java.util.Objects;

public abstract class Variable {

    protected static final String SEPARATOR = ";";

    private final Type type;

    /**
     * FQN of the class where the variable appears.
     */
    private final String name;

    protected Variable(Type type, String name) {
        this.type = type;
        this.name = name;
    }

    public static Variable fromString(String string) {
        String[] split = string.split(SEPARATOR, 2);
        Type type = Type.valueOf(split[0]);
        String name = split[1];

        return new Variable(type, name) {
        };
    }

    public static Variable parse(String string) {
        String[] split = string.split("#");
        if (split.length == 2) {
            return new Field(split[0], split[1]);
        } else if (split.length == 3) {
            return new LocalVariable(split[0], split[1], split[2]);
        }

        throw new IllegalArgumentException("Invalid value for variable: " + string);
    }

    @Override
    public String toString() {
        return type + SEPARATOR + name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Variable)) return false;
        Variable variable = (Variable) o;
        return type == variable.type &&
                name.equals(variable.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, name);
    }

    public Type getType() {
        return type;
    }

    public enum Type {
        FIELD,
        LOCAL_VARIABLE,
        METHOD
    }
}
