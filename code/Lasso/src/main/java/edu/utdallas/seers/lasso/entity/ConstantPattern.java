package edu.utdallas.seers.lasso.entity;

import edu.utdallas.seers.lasso.detector.elements.Const;
import com.ibm.wala.ipa.slicer.NormalStatement;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.util.collections.Pair;

public class ConstantPattern extends Pattern {

  private Statement operand;
  private Const constant;

  public ConstantPattern(Statement operand, Const constant, Statement operator) {
    super(operator, null);
    this.operand = operand;
    this.constant = constant;
  }

  @Override
  public String toString() {
    Pair<String, Integer> pair1 = getClassLinePair((NormalStatement) operand);
    Pair<String, Integer> pair2 = getClassLinePair((NormalStatement) operator);
    StringBuilder sb = new StringBuilder();
    sb.append("operand: ");
    sb.append(pair1.fst);
    sb.append(":");
    sb.append(pair1.snd);
    sb.append(" ");
    sb.append(operand.toString());
    sb.append("\n");

    sb.append("constant: ");
    sb.append(constant.getVal().toString());
    sb.append("\n");

    sb.append("operator: ");
    sb.append(pair2.fst);
    sb.append(":");
    sb.append(pair2.snd);
    sb.append(" ");
    sb.append(operator.toString());
    sb.append("\n");

    return sb.toString();
  }

}
