package edu.utdallas.seers.lasso.detector;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.slicer.NormalStatement;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ipa.slicer.StatementWithInstructionIndex;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.collections.Pair;
import edu.utdallas.seers.lasso.detector.ast.PatternStore;
import edu.utdallas.seers.lasso.detector.elements.Const;
import edu.utdallas.seers.lasso.detector.elements.DetectionSource;
import edu.utdallas.seers.lasso.detector.utils.ConstSlice;
import edu.utdallas.seers.lasso.entity.Pattern;
import edu.utdallas.seers.lasso.entity.PatternType;
import edu.utdallas.seers.lasso.entity.SimplePattern;
import edu.utdallas.seers.lasso.entity.constants.Constant;
import edu.utdallas.seers.lasso.entity.constants.ObjectConstant;
import edu.utdallas.seers.lasso.entity.variables.Variable;

import java.util.*;

// FIXME remove references to patternDetector by properly separating the logic
@SuppressWarnings("deprecation")
public class AssignConstantDetector extends ASTUnionDetector {
    private final PatternDetector patternDetector;

    public AssignConstantDetector(PatternDetector patternDetector) {
        this.patternDetector = patternDetector;
    }

    List<Pattern> detectConstAssign(String operand, String constantString, PatternStore patternStore)
            throws CancelException {
        List<Pattern> detectedPatterns;
        Constant<?> theConstant;

        // TODO do this smarter by specifying the type of constant in the input, e.g. int, bool or field
        if (constantString.contains("#")) {
            detectedPatterns = findObjectAssign(operand, constantString);
            theConstant = new ObjectConstant(constantString);
        } else {
            Const theConst = new Const(constantString);
            theConstant = theConst.toConstant();

            if (operand.split("#").length > 2) {
                detectedPatterns = detectLocVarAssign(operand, theConst);
            } else {
                detectedPatterns = findConstAssign(operand, theConst);
            }
        }

        detectedPatterns.forEach(p -> p.setPatternType(PatternType.ASSIGN_CONSTANT));

        return combinePatterns(detectedPatterns, patternStore, theConstant, Variable.parse(operand));
    }

    private List<Pattern> detectLocVarAssign(String operand, Const constant) {
        List<Pattern> patterns = new ArrayList<>();
        String[] inputSplit = operand.split("#");
        String inputClass = inputSplit[0];
        inputClass = "L" + inputClass.replace('.', '/');
        String inputMet = inputSplit[1];
        String inputVar = inputSplit[2];
        Map<CGNode, Set<Integer>> sliceMap = ConstSlice.computeSliceMap(patternDetector.cg, constant);
        for (Map.Entry<CGNode, Set<Integer>> pair : sliceMap.entrySet()) {
            CGNode node = pair.getKey();
            Set<Integer> values = pair.getValue();
            IR ir = node.getIR();
            if (node.getMethod().getDeclaringClass().getName().toString().equals(inputClass)
                    && node.getMethod().getSelector().getName().toString().equals(inputMet)) {
                if (ir != null) {
                    Iterator<SSAInstruction> instructionIterator = ir.iterateAllInstructions();
                    while (instructionIterator.hasNext()) {
                        SSAInstruction inst = instructionIterator.next();
                        if (inst.iindex > 0) {
                            for (int i = 0; i < inst.getNumberOfUses(); i++) {
                                int vn = inst.getUse(i);
                                if (values.contains(vn)) {
                                    String[] localNames = ir.getLocalNames(inst.iindex, vn);
                                    if (localNames != null) {
                                        for (String name : localNames) {
                                            if (inputVar.equals(name)) {
                                                patterns.add(new SimplePattern(new NormalStatement(node, inst.iindex)));
                                            }
                                        }
                                    }
                                }
                            }
                        }

                    }
                }
            }

        }
        return patterns;
    }

    private List<Pattern> findObjectAssign(String operand, String constantField) throws CancelException {
        List<Pattern> patterns = new ArrayList<>();
        String[] inputSplit = operand.split("#");
        String inputClass = inputSplit[0];
        inputClass = "L" + inputClass.replace('.', '/');
        String inputAttr = inputSplit[1];

        List<Pair<CGNode, SSAPutInstruction>> leftPuts = findPutInstruction(inputClass, inputAttr);

        List<DetectionSource> constSources = patternDetector.findAttributes(constantField, true, true, false, false);
        List<Pair<Statement, Collection<Statement>>> slices = patternDetector.slice(patternDetector.sdg, constSources);
        List<Pair<CGNode, SSAPutInstruction>> rightPuts = findPutInSlice(slices);

        leftPuts.retainAll(rightPuts);

        for (Pair<CGNode, SSAPutInstruction> p : leftPuts) {
            patterns.add(new SimplePattern(new NormalStatement(p.fst, p.snd.iindex)));
        }
        return patterns;
    }

    private List<Pair<CGNode, SSAPutInstruction>> findPutInSlice(
            List<Pair<Statement, Collection<Statement>>> pairs) {
        List<Pair<CGNode, SSAPutInstruction>> ret = new ArrayList<>();
        for (Pair<Statement, Collection<Statement>> pair : pairs) {
            for (Statement stmt : pair.snd) {
                if (stmt instanceof StatementWithInstructionIndex) {
                    StatementWithInstructionIndex s = (StatementWithInstructionIndex) stmt;
                    if (s.getNode()
                            .getMethod()
                            .getReference()
                            .getDeclaringClass()
                            .getClassLoader()
                            .equals(ClassLoaderReference.Application)) {
                        if (s.getInstruction() instanceof SSAPutInstruction) {
                            SSAPutInstruction putInst = (SSAPutInstruction) s.getInstruction();
                            ret.add(Pair.make(s.getNode(), putInst));
                        }
                    }
                }
            }
        }
        return ret;
    }

    private List<Pattern> findConstAssign(String operand, Const constant) {
        String[] inputSplit = operand.split("#");
        if (inputSplit.length < 2) return new ArrayList<>();
        String inputClass = inputSplit[0];
        inputClass = "L" + inputClass.replace('.', '/');
        String inputAttr = inputSplit[1];

        List<Pair<CGNode, SSAPutInstruction>> pairs = findPutInstruction(inputClass, inputAttr);
        List<Pattern> patterns = new ArrayList<>();
        for (Pair<CGNode, SSAPutInstruction> p : pairs) {
            CGNode node = p.fst;
            SSAPutInstruction putInst = p.snd;
            if (patternDetector.matchConstValue(putInst.getUse(1), constant, node.getIR().getSymbolTable())) {
                patterns.add(new SimplePattern(new NormalStatement(node, putInst.iindex)));
            }
        }
        return patterns;
    }

    private List<Pair<CGNode, SSAPutInstruction>> findPutInstruction(String inputClass, String inputAttr) {
        List<Pair<CGNode, SSAPutInstruction>> ret = new ArrayList<>();

        for (CGNode node : patternDetector.cg) {
            IR ir = node.getIR();
            if (ir != null) {
                Iterator<SSAInstruction> instIt = ir.iterateAllInstructions();
                while (instIt.hasNext()) {
                    SSAInstruction inst = instIt.next();
                    if (inst instanceof SSAPutInstruction) {
                        SSAPutInstruction putInst = (SSAPutInstruction) inst;
                        if (putInst
                                .getDeclaredField()
                                .getDeclaringClass()
                                .getName()
                                .toString()
                                .equals(inputClass)
                                && putInst.getDeclaredField().getName().toString().equals(inputAttr)) {
                            ret.add(Pair.make(node, putInst));
                        }
                    }
                }
            }
        }
        return ret;
    }

    @Override
    protected PatternType getPatternType() {
        return PatternType.ASSIGN_CONSTANT;
    }
}