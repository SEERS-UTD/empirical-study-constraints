package sample.argouml81;

class GetterSetter {
    private static final String  CHANGEABLE = "changeable";
    private static final String  ADDONLY = "addonly";

    public void set(Object modelElement, Object value) {
        if (value.equals(CHANGEABLE)) {
            System.out.println(CHANGEABLE);
        } else if (value.equals(ADDONLY)) {
            System.out.println(ADDONLY);
        } else {
            System.out.println("other");
        }
    }
}
