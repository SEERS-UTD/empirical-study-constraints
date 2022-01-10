package edu.utdallas.seers.lasso.detector.utils;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ssa.*;
import com.ibm.wala.util.collections.Pair;
import edu.utdallas.seers.lasso.detector.elements.Const;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

public class Utils {

    public static boolean usesValue(SSAInstruction inst, int vi) {
        for (int i = 0; i < inst.getNumberOfUses(); i++) {
            int v = inst.getUse(i);
            if (v == vi)
                return true;
        }
        return false;
    }

    public static int getVauleUseIndex(SSAInstruction inst, int vi) {
        for (int i = 0; i < inst.getNumberOfUses(); i++) {
            int v = inst.getUse(i);
            if (v == vi)
                return i;
        }
        return -1;
    }

    public static boolean usesConstant(SSAInstruction inst, Const constant, SymbolTable st) {
        for (int i = 0; i < inst.getNumberOfUses(); i++) {
            int v = inst.getUse(i);
            if (isConstant(constant, st, v))
                return true;
        }
        return false;
    }

    public static Collection<Integer> getConstantUses(SSAInstruction inst, Const constant, SymbolTable st) {
        ArrayList<Integer> ret = new ArrayList<>();
        for (int i = 0; i < inst.getNumberOfUses(); i++) {
            int v = inst.getUse(i);
            if (isConstant(constant, st, v))
                ret.add(v);
        }
        return ret;
    }

    public static boolean isConstant(Const constant, SymbolTable st, int v) {
        if (v < 0)
            return false;
        if (!st.isConstant(v))
            return false;
        String val = constant.getVal();
        if (st.isNumberConstant(v)) {
            if (st.isIntegerConstant(v)) {
                int constInt;
                if (constant.getConstType() == Const.ConstType.BOOL) {
                    if ((int) st.getConstantValue(v) == 0 || (int) st.getConstantValue(v) == 1) {
                        return true;
                    }
                } else if ((constant.getConstType() == Const.ConstType.CHAR)) {
                    constInt = (int) st.getConstantValue(v);
                    if (constInt >= -128 && constInt < 127) {
                        if ((char) constInt == constant.getVal().charAt(0)) {
                            return true;
                        }
                    }
                } else if (constant.getConstType() == Const.ConstType.NUM) {
                    if (!isNumeric(val)) {
                        if (val.equals("java.lang.Integer#MAX_VALUE")) {
                            constInt = Integer.MAX_VALUE;
                        } else if (val.equals("java.lang.Integer#MIN_VALUE")) {
                            constInt = Integer.MIN_VALUE;
                        } else {
                            char c = constant.getVal().charAt(0);
                            constInt = c;
                        }
                    } else {
                        try {
                            constInt = Integer.valueOf(val);
                        } catch (NumberFormatException e) {
                            return false;
                        }
                    }
                    if (constInt == (int) st.getConstantValue(v)) {
                        return true;
                    }
                }
            } else if (st.isLongConstant(v)) {
                long constLong = 0;
                if (!isNumeric(constant.getVal())) {
                    if (val.equals("java.lang.Long#MAX_VALUE")) {
                        constLong = Long.MAX_VALUE;
                    } else if (val.equals("java.lang.Long#MIN_VALUE")) {
                        constLong = Long.MIN_VALUE;
                    }
                } else {
                    constLong = Long.valueOf(val);
                }
                if (constLong == (long) st.getConstantValue(v)) {
                    return true;
                }
            } else if (st.isDoubleConstant(v)) {
                double constDouble = 0.0;
                if (!isNumeric(constant.getVal())) {
                    if (val.equals("java.lang.Double#POSITIVE_INFINITY")) {
                        constDouble = Double.POSITIVE_INFINITY;
                    } else if (val.equals("java.lang.Double#NEGATIVE_INFINITY")) {
                        constDouble = Double.NEGATIVE_INFINITY;
                    } else if (val.equals("java.lang.Double#MAX_VALUE")) {
                        constDouble = Double.MAX_VALUE;
                    } else if (val.equals("java.lang.Double#MIN_VALUE")) {
                        constDouble = Double.MIN_VALUE;
                    }
                } else {
                    constDouble = Double.valueOf(val);
                }
                if (constDouble == (double) st.getConstantValue(v)) {
                    return true;
                }
            } else if (st.isFloatConstant(v)) {
                float constFloat = (float) 0.0;
                if (!isNumeric(constant.getVal())) {
                    if (val.equals("java.lang.Float#POSITIVE_INFINITY")) {
                        constFloat = Float.POSITIVE_INFINITY;
                    } else if (val.equals("java.lang.Float#NEGATIVE_INFINITY")) {
                        constFloat = Float.NEGATIVE_INFINITY;
                    } else if (val.equals("java.lang.Float#MAX_VALUE")) {
                        constFloat = Float.MAX_VALUE;
                    } else if (val.equals("java.lang.Float#MIN_VALUE")) {
                        constFloat = Float.MIN_VALUE;
                    }
                } else {
                    constFloat = Float.valueOf(val);
                }
                if (constFloat == (Float) st.getConstantValue(v)) {
                    return true;
                }
            }
        } else if (st.isStringConstant(v)) {
            if (st.getConstantValue(v).equals(constant.getVal())) {
                return true;
            }
        }
        return false;
    }

    public static Collection<Pair<CGNode, Integer>> mapCallerToCallee(CallGraph cg, CGNode node, SSAInvokeInstruction invokeInst, int index) {
        Collection<Pair<CGNode, Integer>> ret = new ArrayList<>();
        CallSiteReference callSiteRef = invokeInst.getCallSite();
        Set<CGNode> possibleTargets = cg.getPossibleTargets(node, callSiteRef);
        for (CGNode t : possibleTargets) {
            IR ir = t.getIR();
            if (ir != null) {
                int[] pNums = t.getIR().getSymbolTable().getParameterValueNumbers();
                ret.add(Pair.make(t, pNums[index]));
            }
        }
        return ret;
    }

    public static Collection<Pair<CGNode, Integer>> mapReturnToCaller(CallGraph cg, CGNode calleeNode) {
        Collection<Pair<CGNode, Integer>> ret = new ArrayList<>();
        Iterator<CGNode> predNodes = cg.getPredNodes(calleeNode);
        while (predNodes.hasNext()) {
            CGNode node = predNodes.next();
            IR ir = node.getIR();
            if (ir != null) {
                Iterator<CallSiteReference> possibleSiteRefs = cg.getPossibleSites(node, calleeNode);
                while (possibleSiteRefs.hasNext()) {
                    CallSiteReference callSiteRef = possibleSiteRefs.next();
                    SSAAbstractInvokeInstruction[] invokeInsts = ir.getCalls(callSiteRef);
                    for (SSAAbstractInvokeInstruction invokeInst : invokeInsts) {
                        if (invokeInst.getNumberOfDefs() > 0) {
                            ret.add(Pair.make(node, invokeInst.getDef()));
                        }
                    }
                }
            }
        }
        return ret;
    }

    public static boolean isNumeric(String str) {
        return str.matches("-?\\d+(\\.\\d+)?");
    }

}
