package edu.utdallas.seers.lasso.entity.constants;

public class ObjectConstant extends Constant<String> {
    public ObjectConstant(String typeQualifiedName, String fieldName) {
        super(Type.OBJECT, typeQualifiedName + "#" + fieldName);
    }

    public ObjectConstant(String value) {
        super(Type.OBJECT, value);
    }
}
