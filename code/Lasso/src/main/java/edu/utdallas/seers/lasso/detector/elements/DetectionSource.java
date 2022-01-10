package edu.utdallas.seers.lasso.detector.elements;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.slicer.*;


public class DetectionSource {

    public enum StmtType {
        NORMAL,
        RET_CALLER,
        RET_CALLEE,
        PARAMCALLER;
    }

    public CGNode node;
    public int index;
    public StmtType type;
    public int v;

    public DetectionSource (CGNode node, Integer index, StmtType type) {
        if (type == StmtType.PARAMCALLER)
            throw new UnsupportedOperationException("cannot create a para source without v number");
        this.node = node;
        this.index = index;
        this.type = type;
        this.v = -1;
    }

    public DetectionSource (CGNode node, Integer index, StmtType type, int v) {
        this.node = node;
        this.index = index;
        this.type = type;
        this.v = v;
    }

    public Statement getSliceSource() {
        switch (type) {
            case NORMAL:
                return new NormalStatement(node, index);
            case RET_CALLER:
                return new NormalReturnCaller(node, index);
            case RET_CALLEE:
                return  new NormalReturnCallee(node);
            case PARAMCALLER:
                return new ParamCaller(node, index, v);
        }
        return null;
    }

    public CGNode getNode() {
        return node;
    }

    public int getIndex() {
        return index;
    }

    @Override
    public String toString() {
        return String.format("%s : %d, %s", node.toString(), index, type.toString());
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }

        if (!(o instanceof DetectionSource)) {
            return false;
        }

        // typecast o to Complex so that we can compare data members
        DetectionSource d = (DetectionSource) o;
        return this.node.equals(d.node) && this.index == d.index && this.type == d.type && this.v == d.v;
    }


}
