package edu.utdallas.seers.lasso.detector;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.slicer.NormalStatement;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.shrikeBT.IConditionalBranchInstruction;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.collections.Pair;
import edu.utdallas.seers.lasso.detector.elements.IfChainStmtWrapper;
import edu.utdallas.seers.lasso.entity.Pattern;
import edu.utdallas.seers.lasso.entity.PatternEntry;
import edu.utdallas.seers.lasso.entity.SimplePattern;

import java.util.*;
import java.util.stream.Collectors;

// TODO this class can be made to inherit from OneInputDetector but we will need to change the return types
public class IfChainDetector {
    List<Pattern> detectPattern(Slice slice) {
        Optional<Pair<Statement, Map<IMethod, Set<IfChainStmtWrapper>>>> matchedPairs =
                findConditionalStatements(slice);

        if (!matchedPairs.isPresent()) {
            return Collections.emptyList();
        }

        List<Pattern> patterns = matchPattern(matchedPairs.get());

        return patterns.stream()
                .peek(p -> p.setPatternType(PatternEntry.PatternType.IF_CHAIN))
                .collect(Collectors.toList());
    }

    List<Pattern> matchPattern(
            Pair<Statement, Map<IMethod, Set<IfChainStmtWrapper>>> condInsts) {
        List<Pattern> ret = new ArrayList<>();
        Map<IMethod, Set<IfChainStmtWrapper>> hm = condInsts.snd;
        for (IMethod m : hm.keySet()) {
            Set<IfChainStmtWrapper> condSet = hm.get(m);
            HashMap<Integer, Set<Statement>> vMap = new HashMap<>();
            for (IfChainStmtWrapper w : condSet) {
                NormalStatement condStmt = w.stmt;
                SSAConditionalBranchInstruction condInst =
                        (SSAConditionalBranchInstruction) condStmt.getInstruction();
                for (int i = 0; i < condInst.getNumberOfUses(); i++) {
                    int v = condInst.getUse(i);
                    if (!vMap.containsKey(v)) {
                        vMap.put(v, new LinkedHashSet<>());
                    }
                    vMap.get(v).add(condStmt);
                }
            }
            for (int v : vMap.keySet()) {
                Statement s = vMap.get(v).iterator().next();
                SymbolTable st = s.getNode().getIR().getSymbolTable();
                if (!st.isConstant(v) && vMap.get(v).size() > 2) {
                    ret.add(new SimplePattern(s));
                }
            }

            HashMap<SSAInvokeInstruction, Statement> invokeMap = new HashMap<>();
            for (IfChainStmtWrapper w : condSet) {
                NormalStatement condStmt = w.stmt;
                IR ir = condStmt.getNode().getIR();
                if (ir != null) {
                    Iterator<SSAInstruction> instIt = ir.iterateAllInstructions();
                    boolean flag = true;
                    while (flag && instIt.hasNext()) {
                        SSAInstruction inst = instIt.next();
                        if (inst instanceof SSAInvokeInstruction) {
                            SSAInvokeInstruction invokeInst = (SSAInvokeInstruction) inst;
                            if (invokeInst.getDeclaredTarget().getName().toString().contains("equals")) {
                                SSAConditionalBranchInstruction condInst =
                                        (SSAConditionalBranchInstruction) condStmt.getInstruction();
                                for (int i = 0; i < condInst.getNumberOfUses(); i++) {
                                    int v = condInst.getUse(i);
                                    if (invokeInst.getReturnValue(0) == v) {
                                        invokeMap.put(invokeInst, condStmt);
                                        flag = false;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            HashMap<Integer, Set<Statement>> callerMap = new HashMap<>();
            HashMap<Integer, Set<Statement>> paraMap = new HashMap<>();

            for (SSAInvokeInstruction inv : invokeMap.keySet()) {
                int v1 = inv.getUse(0);
                int v2 = inv.getUse(1);
                if (!callerMap.containsKey(v1)) callerMap.put(v1, new LinkedHashSet<>());
                callerMap.get(v1).add(invokeMap.get(inv));

                if (!paraMap.containsKey(v2)) paraMap.put(v2, new LinkedHashSet<>());
                paraMap.get(v2).add(invokeMap.get(inv));
            }

            for (int v : callerMap.keySet()) {
                if (callerMap.get(v).size() > 1) {
                    Statement s = callerMap.get(v).iterator().next();
                    System.out.println("caller Map");
                    System.out.println(new SimplePattern(s).toSingleLineFormat().toString());
                    ret.add(new SimplePattern(s));
                }
            }

            for (int v : paraMap.keySet()) {
                if (paraMap.get(v).size() > 1) {
                    Statement s = paraMap.get(v).iterator().next();
                    System.out.println("paraMap");
                    System.out.println(new SimplePattern(s).toSingleLineFormat().toString());
                    ret.add(new SimplePattern(s));
                }
            }
        }

        return ret;
    }

    Optional<Pair<Statement, Map<IMethod, Set<IfChainStmtWrapper>>>> findConditionalStatements(
            Slice slice) {
        HashMap<IMethod, Set<IfChainStmtWrapper>> hm = new HashMap<>();

        for (Statement stmt : slice.getSliceStatements()) {
            if (stmt.getKind() == Statement.Kind.NORMAL) {
                NormalStatement s = (NormalStatement) stmt;
                if (s.getNode()
                        .getMethod()
                        .getReference()
                        .getDeclaringClass()
                        .getClassLoader()
                        .equals(ClassLoaderReference.Application)) {
                    if (s.getInstruction() instanceof SSAConditionalBranchInstruction) {
                        SSAConditionalBranchInstruction condInst =
                                (SSAConditionalBranchInstruction) s.getInstruction();
                        if (condInst.getOperator().equals(IConditionalBranchInstruction.Operator.EQ)
                                || condInst.getOperator().equals(IConditionalBranchInstruction.Operator.NE)) {
                            if (!hm.containsKey(s.getNode().getMethod()))
                                hm.put(s.getNode().getMethod(), new LinkedHashSet<>());
                            hm.get(s.getNode().getMethod()).add(new IfChainStmtWrapper(condInst, s));
                        }
                    }
                }
            }
        }

        if (!hm.isEmpty())
            return Optional.of(Pair.make(slice.getSource(), hm));

        return Optional.empty();
    }
}