package edu.utdallas.seers.lasso.entity;

import com.ibm.wala.classLoader.ShrikeBTMethod;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ipa.slicer.StatementWithInstructionIndex;
import com.ibm.wala.util.collections.Pair;

import java.util.Optional;

public abstract class Pattern {

    // TODO add how far along the slice the pattern was found as final field in constructor and fix all errors
    protected Statement operator;
    private PatternType patternType;

    protected Pattern(Statement operator, PatternType patternType) {
        this.operator = operator;
        this.patternType = patternType;
    }

    public Optional<PatternSingleLineFormat> toSingleLineFormat() {
        if (!(operator instanceof StatementWithInstructionIndex)) {
            return Optional.empty();
        }

        Pair<String, Integer> pair = getClassLinePair((StatementWithInstructionIndex) operator);
        if (pair == null) {
            // this is not a BT method
            return Optional.empty();
        }
        return Optional.of(new PatternSingleLineFormat(pair.fst, pair.snd, false, patternType));
    }

    protected Pair<String, Integer> getClassLinePair(StatementWithInstructionIndex s) {
        int bcIndex, instructionIndex = s.getInstructionIndex();
        try {
            ShrikeBTMethod m = (ShrikeBTMethod) s.getNode().getMethod();
            bcIndex = m.getBytecodeIndex(instructionIndex);
            try {
                int src_line_number = m.getLineNumber(bcIndex);
                String className = m.getDeclaringClass().toString();

                return Pair.make(className, src_line_number);
            } catch (Exception e) {
                System.err.println("Bytecode index no good");
                System.err.println(e.getMessage());
            }
        } catch (Exception e) {
            System.err.println("it's probably not a BT method (e.g. it's a fakeroot method)");
            System.err.println(e.getMessage());
        }
        return null;
    }

    public void setPatternType(PatternType patternType) {
        this.patternType = patternType;
    }
}
