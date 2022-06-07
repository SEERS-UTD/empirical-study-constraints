package edu.utdallas.seers.lasso.detector.matcher;

import com.github.javaparser.ast.stmt.IfStmt;
import edu.utdallas.seers.lasso.entity.ASTPattern;
import edu.utdallas.seers.lasso.entity.PatternType;

import java.util.Collections;
import java.util.List;

public class IfChainMatcher extends PatternMatcher {
    /**
     * Only check the chain if the current node is the first one in order to make the matching
     * unambiguous.
     *
     * @param ifStatement Statement to check.
     * @return {@code true} if the statement is not nested.
     */
    private boolean isFirst(IfStmt ifStatement) {
        return !(ifStatement.getParentNode()
                .orElseThrow(() -> new IllegalStateException("This node should have a parent"))
                instanceof IfStmt);
    }

    @Override
    public List<ASTPattern> visit(IfStmt n, String arg) {
        if (!isFirst(n)) {
            return Collections.emptyList();
        }

        // We only require one nested if
        return n.getElseStmt()
                .filter(e -> e instanceof IfStmt)
                .map(e -> makePattern(n, arg))
                .map(Collections::singletonList)
                .orElse(Collections.emptyList());
    }

    @Override
    public PatternType getPatternType() {
        return PatternType.IF_CHAIN;
    }
}
