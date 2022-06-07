package edu.utdallas.seers.testing;

public class TestUtils {
    /**
     * Wrap in array for easier use of JUnitParams.
     *
     * @param objects objects
     * @return Array
     */
    public static <T> T[] a(T... objects) {
        return objects;
    }
}
