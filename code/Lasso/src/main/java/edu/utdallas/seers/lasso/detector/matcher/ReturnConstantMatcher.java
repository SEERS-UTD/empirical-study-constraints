package edu.utdallas.seers.lasso.detector.matcher;

import com.github.javaparser.ast.stmt.ReturnStmt;
import edu.utdallas.seers.lasso.entity.ASTPattern;
import edu.utdallas.seers.lasso.entity.ValueASTPattern;
import edu.utdallas.seers.lasso.entity.PatternType;

import java.util.Collections;
import java.util.List;

public class ReturnConstantMatcher extends PatternMatcher {

    private final ConstantExtractor constantExtractor = new ConstantExtractor();

    @Override
    public List<ASTPattern> visit(ReturnStmt n, String arg) {
        return n.getExpression()
                .flatMap(e -> constantExtractor.extractConstant(e, false))
                .<ASTPattern>map(c -> new ValueASTPattern(
                        PatternType.RETURN_CONSTANT,
                        arg,
                        nodeToLines(n),
                        c
                ))
                .map(Collections::singletonList)
                .orElse(Collections.emptyList());
    }


    @Override
    public PatternType getPatternType() {
        return PatternType.RETURN_CONSTANT;
    }
}
