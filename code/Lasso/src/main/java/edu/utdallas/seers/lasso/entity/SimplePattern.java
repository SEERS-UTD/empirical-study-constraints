package edu.utdallas.seers.lasso.entity;

import com.ibm.wala.ipa.slicer.Statement;

public class SimplePattern extends Pattern {

    public SimplePattern(Statement operator) {
        super(operator, null);
    }
}
