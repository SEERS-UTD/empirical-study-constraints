package edu.utdallas.seers.lasso.entity;

import com.ibm.wala.ipa.slicer.NormalStatement;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.util.collections.Pair;

// TODO remove this subclass, instead only identify patterns by patternType field
public class NullEmptyPattern extends Pattern {

    private Statement operand;

    public NullEmptyPattern(Statement operand, Statement operator) {
        super(operator, PatternType.NULL_EMPTY_CHECK);
        this.operand = operand;
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
