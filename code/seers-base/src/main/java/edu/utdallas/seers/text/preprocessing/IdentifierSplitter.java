package edu.utdallas.seers.text.preprocessing;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Used to separate camel-cased identifiers into their components. Since this separation is made
 * using regular expressions, the class instance should be reused for better performance.
 */
@SuppressWarnings("unused")
public class IdentifierSplitter {
    private final Pattern pattern1 = Pattern.compile("([A-Z]+)([A-Z][a-z])");
    private final Pattern pattern2 = Pattern.compile("([a-z\\d])([A-Z])");

    /**
     * Implementation taken from Python's inflection library, underscore function
     * https://github.com/jpvanhal/inflection/blob/master/inflection.py
     */
    public List<String> splitIdentifier(String word) {
        Matcher matcher = pattern1.matcher(word);
        word = matcher.replaceAll("$1_$2");

        matcher = pattern2.matcher(word);
        word = matcher.replaceAll("$1_$2");

        return Arrays.asList(word.toLowerCase().split("_"));
    }
}
