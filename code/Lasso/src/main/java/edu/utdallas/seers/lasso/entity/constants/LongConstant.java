package edu.utdallas.seers.lasso.entity.constants;

public class LongConstant extends Constant<Long> {
    public LongConstant(Long value) {
        super(Type.LONG, value);
    }

    @Override
    public Constant<Long> decrement() {
        return new LongConstant(getValue() - 1);
    }

    @Override
    public Constant<Long> increment() {
        return new LongConstant(getValue() + 1);
    }

    @Override
    public Constant<Long> numNegate() {
        return new LongConstant(-getValue());
    }

    @Override
    public Constant<Long> bitComplement() {
        return new LongConstant(~getValue());
    }
}
