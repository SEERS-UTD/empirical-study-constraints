package edu.utdallas.seers.lasso.detector.utils;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.slicer.NormalStatement;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ssa.*;
import com.ibm.wala.util.collections.Pair;
import edu.utdallas.seers.lasso.detector.elements.Const;

import java.util.*;

public class ConstSlice {

    public static Collection<Statement> computeSlice(CallGraph cg, Const source) {
        return computeSlicePair(cg, source).fst;
    }

    public static Map<CGNode, Set<Integer>> computeSliceMap(CallGraph cg, Const source) {
        return computeSlicePair(cg, source).snd;
    }

    private static Pair<Collection<Statement>, Map<CGNode, Set<Integer>>> computeSlicePair(CallGraph cg, Const source) {
        Collection<Statement> ret = new ArrayList<>();

        Queue<Pair<CGNode, Integer>> workList = new LinkedList<>();
        Map<CGNode, Set<Integer>> visited = new HashMap<>();

        // initialize worklist
        Iterator<CGNode> it = cg.iterator();
        while (it.hasNext()) {
            CGNode node = it.next();
            IR ir = node.getIR();
            if (ir != null) {
                Iterator<SSAInstruction> instIt = ir.iterateAllInstructions();
                SymbolTable st = ir.getSymbolTable();
                while (instIt.hasNext()) {
                    SSAInstruction inst = instIt.next();
                    for (int v : Utils.getConstantUses(inst, source, st)) {
                        if (inst.iindex >= 0)
                            ret.add(new NormalStatement(node, inst.iindex));
                        workList.offer(Pair.make(node, v));
                    }
                }
            }
        }

        // solve the worklist
        while (workList.size() > 0) {
            Pair<CGNode, Integer> pair = workList.poll();
            CGNode node = pair.fst;
            int v = pair.snd;
            if (!visited.containsKey(node)) {
                // if method not visited, add to the map
                visited.put(node, new HashSet<>());
            } else if (visited.get(node).contains(v)) {
                // if the method value pair already visited, skip
                continue;
            } else {
                // mark method value pair as visited
                visited.get(node).add(v);
            }

            IR ir = node.getIR();
            if (ir != null) {
                Iterator<SSAInstruction> instIt = ir.iterateAllInstructions();
                while (instIt.hasNext()) {
                    SSAInstruction inst = instIt.next();
                    if (Utils.usesValue(inst, v)) {
                        if (inst instanceof SSAPhiInstruction) {
                            workList.offer(Pair.make(node, inst.getDef()));
                        } else if (inst instanceof SSAInvokeInstruction) {
                            SSAInvokeInstruction invokeInst = (SSAInvokeInstruction) inst;
                            int index = Utils.getVauleUseIndex(inst, v);
                            Collection<Pair<CGNode, Integer>> callees = Utils.mapCallerToCallee(cg, node, invokeInst, index);
                            for (Pair<CGNode, Integer> callee : callees)
                                workList.offer(callee);
                        } else if (inst instanceof SSAReturnInstruction) {
                            Collection<Pair<CGNode, Integer>> callers = Utils.mapReturnToCaller(cg, node);
                            for (Pair<CGNode, Integer> caller : callers)
                                workList.offer(caller);
                        } else if (inst instanceof SSAArrayStoreInstruction) {
                            workList.offer(Pair.make(node, inst.getUse(0)));
                        }
                        if (inst.iindex >= 0)
                            ret.add(new NormalStatement(node, inst.iindex));
                    }
                }
            }
        }
        return Pair.make(ret, visited);
    }

}