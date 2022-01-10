package edu.utdallas.seers.lasso.entity;

import com.ibm.wala.ipa.slicer.NormalStatement;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.util.collections.Pair;

public class ThreeWayPattern extends Pattern {

  private Statement operand1;
  private Statement operand2;

  public ThreeWayPattern(Statement operand1, Statement operand2, Statement operator) {
    super(operator, null);
    this.operand1 = operand1;
    this.operand2 = operand2;
  }

  @Override
  public String toString() {
    Pair<String, Integer> pair1 = getClassLinePair((NormalStatement) operand1);
    Pair<String, Integer> pair2 = getClassLinePair((NormalStatement) operand2);
    Pair<String, Integer> pair3 = getClassLinePair((NormalStatement) operator);
    StringBuilder sb = new StringBuilder();
    sb.append("operand1: ");
    sb.append(pair1.fst);
    sb.append(":");
    sb.append(pair1.snd);
    sb.append(" ");
    sb.append(operand1.toString());
    sb.append("\n");

    sb.append("operand2: ");
    sb.append(pair2.fst);
    sb.append(":");
    sb.append(pair2.snd);
    sb.append(" ");
    sb.append(operand2.toString());
    sb.append("\n");

    sb.append("operator: ");
    sb.append(pair3.fst);
    sb.append(":");
    sb.append(pair3.snd);
    sb.append(" ");
    sb.append(operator.toString());
    // sb.append("equals()");
    sb.append("\n");

    return sb.toString();
  }
}
