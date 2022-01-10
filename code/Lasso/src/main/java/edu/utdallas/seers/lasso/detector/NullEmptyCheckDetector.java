package edu.utdallas.seers.lasso.detector;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.ibm.wala.ipa.slicer.NormalStatement;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.util.collections.Pair;
import edu.utdallas.seers.lasso.entity.NullEmptyPattern;
import edu.utdallas.seers.lasso.entity.Pattern;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

// TODO: Use this template to create superclasses for other detectors
public class NullEmptyCheckDetector {

    public static boolean match(Node node) {
        if (!(node instanceof BinaryExpr)) {
            return false;
        }

        BinaryExpr expression = (BinaryExpr) node;

        // TODO: do we want to allow x.equals(null)? this code would not allow that
        BinaryExpr.Operator operator = expression.getOperator();
        // Operator is == or != and one of the expressions is a null literal
        return (operator.equals(BinaryExpr.Operator.EQUALS) ||
                operator.equals(BinaryExpr.Operator.NOT_EQUALS)) &&
                (expression.getLeft().isNullLiteralExpr() ||
                        expression.getRight().isNullLiteralExpr());
    }

    /**
     * Finds the instances of this pattern in the slice.
     *
     * @param slice A slice to match on.
     * @return A list of pattern instances that were found in the slice,
     */
    List<Pattern> detectPattern(Slice slice) {
        List<Pair<Statement, Collection<Statement>>> list = Collections.singletonList(Pair.make(slice.getSource(), slice.getSliceStatements()));
        List<Pair<Statement, List<Statement>>> matchedStatements = PatternDetector.findOperatorConst(list, "eq");

        List<Pattern> ret = new ArrayList<>();
        for (Pair<Statement, List<Statement>> pair1 : matchedStatements) {
            Statement source = pair1.fst;
            List<Statement> stmts = pair1.snd;
            for (Statement operator : stmts) {
                if (operator instanceof NormalStatement) {
                    NormalStatement s = (NormalStatement) operator;
                    SymbolTable st = operator.getNode().getIR().getSymbolTable();

                    for (int i = 0; i < s.getInstruction().getNumberOfUses(); i++) {
                        int v = s.getInstruction().getUse(i);
                        if (st.isNullConstant(v)
                                || (st.isStringConstant(v) && st.getConstantValue(v).equals(""))) {
                            Pattern pattern = new NullEmptyPattern(source, operator);
                            ret.add(pattern);
                        }
                    }
                }
            }
        }

        return ret;
    }
}