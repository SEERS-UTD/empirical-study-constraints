package edu.utdallas.seers.lasso.detector;

import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.util.collections.Pair;

import java.util.Collection;

public class Slice {

    private final Statement source;
    private final Collection<Statement> sliceStatements;

    public Slice(Statement source, Collection<Statement> sliceStatements) {
        this.source = source;
        this.sliceStatements = sliceStatements;
    }

    public Statement getSource() {
        return source;
    }

    public Collection<Statement> getSliceStatements() {
        return sliceStatements;
    }
}
