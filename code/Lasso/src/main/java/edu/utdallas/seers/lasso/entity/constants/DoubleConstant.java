package edu.utdallas.seers.lasso.entity.constants;

public class DoubleConstant extends Constant<Double> {
    public DoubleConstant(Double value) {
        super(Type.DOUBLE, value);
    }

    @Override
    public Constant<Double> decrement() {
        return new DoubleConstant(getValue()-1);
    }

    @Override
    public Constant<Double> increment() {
        return new DoubleConstant(getValue()+1);
    }

    @Override
    public Constant<Double> numNegate() {
        return new DoubleConstant(-getValue());
    }
}
