package edu.utdallas.seers.lasso.detector.elements;

public abstract class ClassAttribute {

    private String className;

    public ClassAttribute(String className) {
        this.className = className;
    }

    public String getClassName() {
        return this.className;
    }

}
