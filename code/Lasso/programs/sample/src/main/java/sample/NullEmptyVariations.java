package sample;

@SuppressWarnings("unused")
public class NullEmptyVariations {
    String property;

    public void test() {
        if (property == null || property.equals("")) {
            System.out.println("null-empty-check");
        }

        if (property == null || property.isEmpty()) {
            System.out.println("null-boolean-check");
        }

        if (property == null) {
            System.out.println("null-check");
        }

        if (property == null || property.length() == 0) {
            System.out.println("null-zero-check");
        }
    }
}
