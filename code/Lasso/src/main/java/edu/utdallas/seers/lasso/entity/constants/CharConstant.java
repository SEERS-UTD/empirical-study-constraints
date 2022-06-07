package edu.utdallas.seers.lasso.entity.constants;

public class CharConstant extends Constant<Character> {
    public CharConstant(Character value) {
        super(Type.CHAR, value);
    }

    @Override
    public Constant<Character> decrement() {
        return new CharConstant((char) (getValue() - 1));
    }

    @Override
    public Constant<Character> increment() {
        return new CharConstant((char) (getValue() + 1));
    }

    @Override
    public Constant<Character> numNegate() {
        return new CharConstant((char) -getValue());
    }

    @Override
    public Constant<Character> bitComplement() {
        return new CharConstant((char) ~getValue());
    }
}
