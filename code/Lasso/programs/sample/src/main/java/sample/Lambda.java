package sample;

import sample.abs.Task;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class Lambda {
    public static void aaa() {
        doReportSystemProperties(System.out, new Lambda());
    }

    private static void bbb() {
        System.out.println("B");
    }

    private static void bbb(String a) {
        System.out.println(a);
    }

    private static void doReportSystemProperties(PrintStream out, Lambda el) {
        List<String> l = Arrays.asList("a", "b");
        l.forEach(el::act);
        l.forEach(out::println);
    }

    public String doEverything(Object memeFace, Task me) {
        System.out.println(memeFace + " " + me);

        return "a" + memeFace + me;
    }

    private void act(String s) {
        System.out.println("me");
    }

    private static String getProperty(String key) {
        String value;
        try {
            value = System.getProperty(key);
        } catch (SecurityException e) {
            value = "";
        }
        return value;
    }

    public Rectangle2D getBoundingBox(BufferedImage image, AffineTransform tx) {
        int xmax = image.getWidth() - 1;
        int ymax = image.getHeight() - 1;
        Point2D[] corners = new Point2D.Double[4];
        corners[0] = new Point2D.Double(0, 0);
        corners[1] = new Point2D.Double(xmax, 0);
        corners[2] = new Point2D.Double(xmax, ymax);
        corners[3] = new Point2D.Double(0, ymax);
        tx.transform(corners, 0, corners, 0, 4);

        // Create bounding box of transformed corner points
        Rectangle2D boundingBox = new Rectangle2D.Double();
        Arrays.stream(corners, 0, 4).forEach(boundingBox::add);
        return boundingBox;
    }
}
