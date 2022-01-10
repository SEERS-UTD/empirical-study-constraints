package sample;

public class SampleClass {
    public static final int INT_CONSTANT = 10;
    private final int intMember;

    public SampleClass(int intArg) {
        intMember = intArg;
    }

    public void execute() {
        if (intMember < INT_CONSTANT) {
            System.out.println("Number is smaller");
        }
    }
}
