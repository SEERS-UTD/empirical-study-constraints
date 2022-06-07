package edu.utdallas.seers.lasso.entity.constants;

import java.util.Objects;
import java.util.function.Function;

public abstract class Constant<T> {
    private static final String SEPARATOR = ";";
    private final Type type;
    private final T value;

    protected Constant(Type type, T value) {
        this.type = type;
        this.value = value;
    }

    public static Constant<?> fromString(String string) {
        // Limit -1 preserves trailing empty delimiters, i.e. the "" string constant
        String[] split = string.split(SEPARATOR, 2);
        Type type = Type.valueOf(split[0]);

        // Intentionally using anonymous class here
        // Constants serialized from file don't need to be converted, so no methods need to be overridden
        return new Constant<Object>(type, type.reader.apply(split[1])) {
        };
    }

    public Constant<T> decrement() {
        throw new RuntimeException("Method not valid for this constant");
    }

    public Constant<T> increment() {
        throw new RuntimeException("Method not valid for this constant");
    }

    public Constant<T> numNegate() {
        throw new RuntimeException("Method not valid for this constant");
    }

    public Constant<T> boolNegate() {
        throw new RuntimeException("Method not valid for this constant");
    }

    public Constant<T> bitComplement() {
        throw new RuntimeException("Method not valid for this constant");
    }

    @Override
    public String toString() {
        return type.toString() + SEPARATOR + type.writer.apply(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Constant)) return false;
        Constant<?> constant = (Constant<?>) o;
        return type == constant.type &&
                value.equals(constant.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, value);
    }

    public Type getType() {
        return type;
    }

    public T getValue() {
        return value;
    }

    public enum Type {
        BOOLEAN(Boolean::valueOf, Object::toString),
        INTEGER(Integer::valueOf, Object::toString),
        DOUBLE(Double::valueOf, Object::toString),
        // Serializing using the int value because it breaks if they use strange characters like \uD800
        CHAR(s -> Character.valueOf((char) Integer.parseInt(s)), c-> Integer.valueOf(((char) c)).toString()),
        LONG(Long::valueOf, Object::toString),
        STRING(Function.identity(), Object::toString),
        /**
         * For constants such as Singleton.instance
         */
        OBJECT(Function.identity(), Object::toString);

        private final Function<String, ?> reader;
        private final Function<Object, String> writer;

        Type(Function<String, ?> reader, Function<Object, String> writer) {
            this.reader = reader;
            this.writer = writer;
        }
    }
}
