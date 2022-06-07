package edu.utdallas.seers.lasso.detector.elements;

import edu.utdallas.seers.lasso.detector.utils.Utils;
import edu.utdallas.seers.lasso.entity.constants.*;

import java.util.HashMap;
import java.util.Map;

// TODO merge with Constant class
@SuppressWarnings("DeprecatedIsStillUsed")
@Deprecated
public class Const {
    private static final Map<String, Constant<?>> NAMED_CONSTS = new HashMap<>();
    static {
        NAMED_CONSTS.put("java.lang.Integer#MAX_VALUE", new IntegerConstant(Integer.MAX_VALUE));
        NAMED_CONSTS.put("java.lang.Integer#MIN_VALUE", new IntegerConstant(Integer.MIN_VALUE));
        NAMED_CONSTS.put("java.lang.Long#MAX_VALUE", new LongConstant(Long.MAX_VALUE));
        NAMED_CONSTS.put("java.lang.Long#MIN_VALUE", new LongConstant(Long.MIN_VALUE));
        NAMED_CONSTS.put("java.lang.Double#POSITIVE_INFINITY", new DoubleConstant(Double.POSITIVE_INFINITY));
        NAMED_CONSTS.put("java.lang.Double#NEGATIVE_INFINITY", new DoubleConstant(Double.NEGATIVE_INFINITY));
        NAMED_CONSTS.put("java.lang.Double#MAX_VALUE", new DoubleConstant(Double.MAX_VALUE));
        NAMED_CONSTS.put("java.lang.Double#MIN_VALUE", new DoubleConstant(Double.MIN_VALUE));
    }

  private String val;
  private final ConstType constType;

    public Constant<?> toConstant() {
        switch (constType) {
            case STR:
                return new StringConstant(val);
            case BOOL:
                return new BooleanConstant(val.equals("TRUE"));
            case CHAR:
                return new CharConstant(val.charAt(0));
            case NUM:
                if (NAMED_CONSTS.containsKey(val)) {
                    return NAMED_CONSTS.get(val);
                }

                if (val.endsWith("L")) {
                    return new LongConstant(Long.parseLong(val.replace("L", "")));
                }

                try {
                    return new IntegerConstant(Integer.parseInt(val));
                } catch (NumberFormatException e) {
                    return new DoubleConstant(Double.parseDouble(val));
                }
            default:
                return null;
        }
    }

    public enum ConstType {
    NUM,
    STR,
    BOOL,
    CHAR
  }

  public Const(String val) {
    this.val = val;
    if (Utils.isNumeric(val)
            // FIXME this cannot be right
        || val.length() == 1
        || NAMED_CONSTS.containsKey(val)) {
        this.constType = ConstType.NUM;
    }else if (Utils.isNumeric(val.replaceAll("L$",""))){
        this.constType = ConstType.NUM;
        this.val = val.substring(0, val.length() - 1);
    } else if (val.equals("TRUE") || val.equals("FALSE")) {
      this.constType = ConstType.BOOL;
    } else if (val.equals("\\n")) {
      this.constType = ConstType.CHAR;
    } else {
      this.constType = ConstType.STR;

    }
  }

  public Const(String val, ConstType constType) {
    this.val = val;
    this.constType = constType;
  }

  public String getVal() {
    return val;
  }

  public ConstType getConstType() {
    return constType;
  }
}
