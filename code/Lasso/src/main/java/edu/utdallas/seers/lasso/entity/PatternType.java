package edu.utdallas.seers.lasso.entity;

import edu.utdallas.seers.lasso.detector.matcher.*;

/**
 * Contains all implemented patterns.
 */
public enum PatternType {
    ASSIGN_CONSTANT(AssignConstantMatcher.class),
    BINARY_COMPARISON(BinaryComparisonMatcher.class),
    BINARY_FLAG_CHECK(BinaryFlagCheckMatcher.class),
    BOOLEAN_PROPERTY(BooleanPropertyMatcher.class),
    CONSTANT_ARGUMENT(ConstantArgumentMatcher.class),
    EQUALS_OR_CHAIN(EqualsOrChainMatcher.class),
    IF_CHAIN(IfChainMatcher.class),
    NULL_CHECK(NullCheckMatcher.class),
    NULL_EMPTY_CHECK(NullEmptyCheckMatcher.class),
    NULL_ZERO_CHECK(NullZeroCheckMatcher.class),
    RETURN_CONSTANT(ReturnConstantMatcher.class),
    SELF_COMPARISON(SelfComparisonMatcher.class),
    STR_FORMAT(StrFormatMatcher.class),
    SWITCH_LEN_CHAR(SwitchLenCharMatcher.class);

    private final PatternMatcher matcher;

    static {
        // Checking that the matchers were correctly set
        for (PatternType value : values()) {
            assert value.equals(value.matcher.getPatternType());
        }
    }

    PatternType(Class<? extends PatternMatcher> theClass) {
        try {
            this.matcher = theClass.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    public static PatternType fromString(String s) {
        return valueOf(s.toUpperCase().replace("-", "_"));
    }

    public String toInputString() {
        return name().toLowerCase().replace('_', '-');
    }

    public PatternMatcher getMatcher() {
        return matcher;
    }
}
