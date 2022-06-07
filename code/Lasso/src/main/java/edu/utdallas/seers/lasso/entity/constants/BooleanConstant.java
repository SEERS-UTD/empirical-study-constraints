package edu.utdallas.seers.lasso.entity.constants;

public class BooleanConstant extends Constant<Boolean> {

    public BooleanConstant(Boolean value) {
        super(Type.BOOLEAN, value);
    }

    @Override
    public Constant<Boolean> boolNegate() {
        return new BooleanConstant(!getValue());
    }
}
