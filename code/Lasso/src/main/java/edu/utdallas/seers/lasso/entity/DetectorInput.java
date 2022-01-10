package edu.utdallas.seers.lasso.entity;

import edu.utdallas.seers.lasso.detector.elements.Const;

import java.util.Objects;

public class DetectorInput {

    private final String identifier;
    private final Const value;
    private final Type type;

    private DetectorInput(String identifier, Const value, Type type) {
        this.identifier = identifier;
        this.value = value;
        this.type = type;
    }

    public static DetectorInput parseInput(String s) {
        if (s.contains("=")) {
            String[] split = s.split("=");
            return new DetectorInput(split[0], parseLiteral(split[1]), Type.FINAL_CONSTANT);
        }

        return new DetectorInput(s, null, Type.REGULAR);
    }

    private static Const parseLiteral(String s) {
        if (s.startsWith("\"")) {
            return new Const(s.substring(1, s.length() - 1), Const.ConstType.STR);
        } else {
            return new Const(s, Const.ConstType.NUM);
        }
    }

    @Override
    public String toString() {
        return "DetectorInput{" +
                "identifier='" + identifier + '\'' +
                ", value=" + value +
                ", type=" + type +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DetectorInput that = (DetectorInput) o;
        return Objects.equals(identifier, that.identifier) &&
                Objects.equals(value, that.value) &&
                type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier, value, type);
    }

    public String getIdentifier() {
        return identifier;
    }

    public Const getValue() {
        return value;
    }

    public Type getType() {
        return type;
    }

    public enum Type {
        FINAL_CONSTANT,
        /**
         * Means a field, method return or local variable.
         * TODO: make each one of those a type and add one for literal
         */
        REGULAR
    }
}
