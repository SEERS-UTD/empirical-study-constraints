package sample.swarm52;

import java.text.ParseException;

@SuppressWarnings("ALL")
public class Pick {
    private Onset onset;

    public Pick(Node node) {
        try {
            onset = Onset.parse(node.getTextContent());
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    public enum Onset {
        EMERGENT, IMPULSIVE, QUESTIONABLE;

        /**
         * Parse an Onset from a String.
         *
         * @param string onset
         * @return onset object
         * @throws ParseException when things go wrong
         */
        public static Onset parse(String string) throws ParseException {
            if ("emergent".equals(string)) {
                return EMERGENT;
            } else if ("impulsive".equals(string)) {
                return IMPULSIVE;
            } else if ("questionable".equals(string)) {
                return QUESTIONABLE;
            } else {
                throw new ParseException("Cannot parse " + string, 12);
            }
        }
    }
}
