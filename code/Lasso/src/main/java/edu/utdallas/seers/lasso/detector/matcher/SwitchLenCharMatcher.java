package edu.utdallas.seers.lasso.detector.matcher;

import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import edu.utdallas.seers.lasso.entity.ASTPattern;
import edu.utdallas.seers.lasso.entity.PatternType;
import edu.utdallas.seers.lasso.entity.constants.Constant;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class SwitchLenCharMatcher extends PatternMatcher {

    private final ConstantExtractor constantExtractor = new ConstantExtractor();

    @Override
    public List<ASTPattern> visit(SwitchStmt n, String arg) {
        if (n.getParentNode()
                .orElseThrow(() -> new IllegalStateException("This node should have a parent"))
                instanceof SwitchEntry) {
            // It is nested inside another switch
            return Collections.emptyList();
        }

        // All labels must be integer constants
        Supplier<Boolean> condition = () -> n.getEntries().stream()
                .allMatch(e -> e.getLabels().stream()
                        .allMatch(l -> constantExtractor.extractConstant(l, false)
                                .map(c -> c.getType() == Constant.Type.INTEGER)
                                .orElse(false)
                        ));

        return makeReturnPattern(n, arg, condition);
    }

    @Override
    public PatternType getPatternType() {
        return PatternType.SWITCH_LEN_CHAR;
    }
}
