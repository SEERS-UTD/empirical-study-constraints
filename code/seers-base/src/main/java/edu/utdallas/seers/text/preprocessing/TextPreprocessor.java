package edu.utdallas.seers.text.preprocessing;

import opennlp.tools.stemmer.PorterStemmer;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class TextPreprocessor {
    private final Pattern tokenizer = Pattern.compile("[\\w]+(?:'\\w)?");
    private final List<String> stopWords = Preprocessing.loadStandardStopWords();
    private final PorterStemmer stemmer = new PorterStemmer();
    private final IdentifierSplitter splitter = new IdentifierSplitter();

    public Stream<String> tokenize(String string) {
        Matcher matcher = tokenizer.matcher(string);

        if (matcher.find()) {
            // Don't think there is another way of turning this into a stream
            return Stream.iterate(matcher.group(), Objects::nonNull,
                    n -> {
                        if (matcher.find()) {
                            return matcher.group();
                        }

                        return null;
                    });
        }

        return Stream.empty();
    }

    /**
     * Filters stop words and short words.
     *
     * @param words The words. Must be in lower case.
     * @return Filtered words.
     */
    public Stream<String> filterWords(Stream<String> words) {
        return words
                .filter(w -> w.length() > 2 && !stopWords.contains(w));
    }

    /**
     * Tokenizes, splits identifiers, filters words, and optionally stems.
     *
     * @param string String for preprocessing
     * @param stem   Whether or not to stem.
     * @return Tokens
     * @see TextPreprocessor#preprocess(Stream, boolean)
     * @see TextPreprocessor#tokenize(String)
     */
    public Stream<String> preprocess(String string, boolean stem) {
        Stream<String> tokens = tokenize(string);

        return preprocess(tokens, stem);
    }

    /**
     * Splits identifiers, filters words, and optionally stems.
     *
     * @param tokens Text tokens.
     * @param stem   Whether or not to stem.
     * @return Preprocessed tokens.
     * @see TextPreprocessor#filterWords(Stream)
     */
    public Stream<String> preprocess(Stream<String> tokens, boolean stem) {
        Stream<String> split = tokens
                .flatMap(w -> splitter.splitIdentifier(w).stream());

        Stream<String> filtered = filterWords(split);

        if (stem) {
            return filtered
                    .map(stemmer::stem);
        } else {
            return filtered;
        }
    }
}
