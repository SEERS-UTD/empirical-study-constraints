package edu.utdallas.seers.lasso.detector.elements;

import com.ibm.wala.ipa.slicer.NormalStatement;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;

public class IfChainStmtWrapper {
    public SSAConditionalBranchInstruction condInst;
    public NormalStatement stmt;

    public IfChainStmtWrapper(SSAConditionalBranchInstruction condInst, NormalStatement stmt) {
        this.condInst = condInst;
        this.stmt = stmt;
    }

    @Override
    public boolean equals(Object that) {
        if (that == this) {
            return true;
        }
        if (!(that instanceof IfChainStmtWrapper)) {
            return false;
        }
        IfChainStmtWrapper w = (IfChainStmtWrapper) that;
        return this.condInst.equals(w.condInst);
    }

    @Override
    public int hashCode() {
        return condInst.hashCode();
    }
}
