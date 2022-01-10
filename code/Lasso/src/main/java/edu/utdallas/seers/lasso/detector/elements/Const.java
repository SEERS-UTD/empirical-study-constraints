package edu.utdallas.seers.lasso.detector.elements;

import edu.utdallas.seers.lasso.detector.utils.Utils;

public class Const extends ClassAttribute {
  private String val;
  private ConstType constType;

  public enum ConstType {
    NUM,
    STR,
    BOOL,
    CHAR;
  }

  public Const(String val) {
    super(null);
    this.val = val;
    if (Utils.isNumeric(val)
        || val.length() == 1
        || val.equals("java.lang.Integer#MAX_VALUE")
        || val.equals("java.lang.Integer#MIN_VALUE")
        || val.equals("java.lang.Long#MAX_VALUE")
        || val.equals("java.lang.Long#MIN_VALUE")
        || val.equals("java.lang.Double#POSITIVE_INFINITY")
        || val.equals("java.lang.Double#NEGATIVE_INFINITY")
        || val.equals("java.lang.Double#MAX_VALUE")
        || val.equals("java.lang.Double#MIN_VALUE")) {
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
    super(null);
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
