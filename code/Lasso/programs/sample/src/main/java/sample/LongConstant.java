package sample;

public class LongConstant {
    public void methodOne() {
        methodTwo(-12219292800000L);
    }

    @SuppressWarnings("SameParameterValue")
    private void methodTwo(long l) {
        System.out.println(l);
    }
}
