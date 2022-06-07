package edu.utdallas.seers.lasso.entity;

import com.google.gson.annotations.SerializedName;

import java.util.*;

public class PatternEntry {

    public PatternOutputFormat toOutputFormat(List<Pattern> patterns) {
        List<PatternSingleLineFormat> detected = new ArrayList<>();
        // FIXME instead of converting each pattern to single line, let them have multiple lines if that is how they were detected
        for (Pattern pattern : patterns) {
            Optional<PatternSingleLineFormat> pslf = pattern.toSingleLineFormat();
            pslf.ifPresent(detected::add);
        }
        Set<PatternSingleLineFormat> set = new HashSet<>(detected);
        detected.clear();
        detected.addAll(set);

        List<PatternSingleLineFormat> falseNegatives = getgrdTruthinSingleLineFormat();
        List<PatternSingleLineFormat> falsePositives = new ArrayList<>();
        List<PatternSingleLineFormat> truePositives = new ArrayList<>();

        for (PatternSingleLineFormat p : detected) {
            boolean flag = false;
            for (int i = 0; i < falseNegatives.size(); i++) {
                PatternSingleLineFormat fn = falseNegatives.get(i);
                if (p.getLineNum() == fn.getLineNum() &&
                        p.getFile().equals(fn.getFile())) {
                    flag = true;
                    truePositives.add(p);
                    falseNegatives.remove(i);
                    break;
                }
            }
            if (!flag) falsePositives.add(p);
        }

        // FIXME assumes there is only one ground truth
        if (!truePositives.isEmpty()) {
            falseNegatives = Collections.emptyList();
        }

        return new PatternOutputFormat(
                this,
                truePositives,
                falsePositives,
                falseNegatives);
    }

    // TODO get rid of this enum
  @SuppressWarnings("unused")
  public enum PatternType {
    ASSIGN_CONSTANT,
    BINARY_COMPARISON,
    BINARY_FLAG_CHECK,
    BOOLEAN_FLAG_LOOP,
    BOOLEAN_PROPERTY,
    CONSTANT_ARGUMENT,
    CONSTRUCTOR_ASSIGN,
    CONSUME_UNTIL,
    DATABASE_DEFAULT,
    DATABASE_ENUM,
    DB_REGEX,
    DELTA_CHECK,
    ENUM_VALUEOF,
    EQUALS_OR_CHAIN,
    EXCEPTION_BRANCHING,
    EXCEPTION_COND_EXECUTION,
    EXTERNAL,
    IF_CHAIN,
    ITERATE_AND_CHECK_LITERAL,
    MOD_OP,
    MULTI_REGEX,
    NULLABLE_BOOLEAN,
    NULL_BOOLEAN_CHECK,
    NULL_CHECK,
    NULL_EMPTY_CHECK,
    NULL_ZERO_CHECK,
    POLYMORPHIC_METHOD,
    PROPERTIES_FILE,
    REGEX,
    RETURN_CONSTANT,
    SELF_COMPARISON,
    SETTER,
    STR_CONCAT,
    STR_CONTAINS,
    STR_ENDS,
    STR_FORMAT,
    STR_STARTS,
    SWITCH_CASE,
    SWITCH_CONVERSION,
    SWITCH_LEN_CHAR;

    public String toInputString() {
      return name().toLowerCase().replace('_', '-');
    }

    public static PatternType fromString(String s) {
      return valueOf(s.toUpperCase().replace("-", "_"));
    }

    public int getID() {
      switch (this) {
        case BINARY_COMPARISON:
          return 0;
        case NULL_EMPTY_CHECK:
          return 1;
        case REGEX:
          return 2;
        case BOOLEAN_PROPERTY:
          return 3;
        case CONSTANT_ARGUMENT:
          return 4;
        case ASSIGN_CONSTANT:
          return 5;
        case BINARY_FLAG_CHECK:
          return 6;
        case IF_CHAIN:
          return 7;
        default:
          return -1;
      }
    }
  }

  public enum ConstraintType {
//    @SerializedName("comparison")
//    VALUE_COMPARISON,
//    @SerializedName("has-default")
//    DEFAULT_VALUE,
//    @SerializedName("options")
//    CATEGORICAL_VALUE,
//    @SerializedName("property-check")
//    BINARY_VALUE,
//    @SerializedName("text-template")
//    STRING_PATTERN,
    @SerializedName("value-comparison")
    VALUE_COMPARISON,
    @SerializedName("dual-value-comparison")
    DUAL_VALUE_COMPARISON,
    @SerializedName("concrete-value")
    CONCRETE_VALUE,
    @SerializedName("categorical-value")
    CATEGORICAL_VALUE;

    public String toInputString() {
      return this.name().toLowerCase().replace("_", "-");
    }

    public static ConstraintType fromString(String s) {
      return ConstraintType.valueOf(s.toUpperCase().replace('-', '_'));
    }

    public int getID() {
      switch (this) {
        case VALUE_COMPARISON:
          return 0;
        case CATEGORICAL_VALUE:
          return 2;
        default:
          return -1;
      }
    }
  }

  @SerializedName("entryPoint")
  private String entryPoint;

  @SerializedName("constraintId")
  private String constraintId;

  @SerializedName("constraint")
  private ConstraintType cType;

  @SerializedName("groundTruth")
  private PatternTruth[] pTrus;

  @SerializedName("inputs")
  private List<DetectorInput> inputs;

  @SerializedName("pattern")
  private PatternType pType;

  @SerializedName("system")
  private String system;

  public String getEntryPoint() {
    return entryPoint;
  }

  public String getConstraintId() {
    return constraintId;
  }

  public ConstraintType getcType() {
    return cType;
  }

  public PatternTruth[] getpTrus() {
    return pTrus;
  }

  public List<DetectorInput> getInputs() {
    return inputs;
  }

  public PatternType getpType() {
    return pType;
  }

  public String getSystem() {
    return system;
  }

  public List<PatternSingleLineFormat> getgrdTruthinSingleLineFormat() {
    List<PatternSingleLineFormat> ret = new ArrayList<>();
    for (PatternTruth t : pTrus) {
      ret.addAll(t.toSingleLineFormat());
    }
    return ret;
  }

  public PatternEntry(String constraintId, PatternTruth[] pTrus, List<DetectorInput> inputs, String system, ConstraintType constraintType, PatternType patternType) {
    this.constraintId = constraintId;
    this.pTrus = pTrus;
    this.inputs = inputs;
    this.system = system;
    this.cType = constraintType;
    pType = patternType;
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    //    sb.append("entry point: ");
    //    sb.append(entryPoint);
    //    sb.append("\n");

    sb.append("ground truth: \n[");
    for (PatternTruth pt : pTrus) {
      sb.append(pt.toString());
    }
    sb.append("]\n");

        sb.append(inputs.toString());

        sb.append("pattern: ");

        sb.append(pType.toString().toLowerCase().replace("_", "-"));

        sb.append("]\n");

        sb.append("inputs: ");
        for (DetectorInput inp : inputs) {
            sb.append(inp);
        }

        return sb.toString();
    }
}
