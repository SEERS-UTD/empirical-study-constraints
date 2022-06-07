package edu.utdallas.seers.lasso.detector.matcher;

import com.github.javaparser.ast.expr.MethodCallExpr;
import edu.utdallas.seers.lasso.entity.ASTPattern;
import edu.utdallas.seers.lasso.entity.PatternType;

import java.util.List;

public class StrFormatMatcher extends PatternMatcher {
    @Override
    public List<ASTPattern> visit(MethodCallExpr n, String arg) {
        return makeReturnPattern(n, arg, () -> n.getName().toString().equals("format"));
    }

    @Override
    public PatternType getPatternType() {
        return PatternType.STR_FORMAT;
    }
}
