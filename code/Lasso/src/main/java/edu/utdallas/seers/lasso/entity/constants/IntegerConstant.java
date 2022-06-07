package edu.utdallas.seers.lasso.entity.constants;

public class IntegerConstant extends Constant<Integer> {
    public IntegerConstant(Integer value) {
        super(Type.INTEGER, value);
    }

    @Override
    public Constant<Integer> decrement() {
        return new IntegerConstant(getValue() - 1);
    }

    @Override
    public Constant<Integer> increment() {
        return new IntegerConstant(getValue() + 1);
    }

    @Override
    public Constant<Integer> numNegate() {
        return new IntegerConstant(-getValue());
    }

    @Override
    public Constant<Integer> bitComplement() {
        return new IntegerConstant(~getValue());
    }
}
