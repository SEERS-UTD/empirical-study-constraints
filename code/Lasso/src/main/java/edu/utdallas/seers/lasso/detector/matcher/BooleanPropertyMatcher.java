package edu.utdallas.seers.lasso.detector.matcher;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.resolution.Resolvable;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.resolution.types.ResolvedPrimitiveType;
import com.github.javaparser.resolution.types.ResolvedType;
import edu.utdallas.seers.lasso.entity.ASTPattern;
import edu.utdallas.seers.lasso.entity.PatternType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class BooleanPropertyMatcher extends PatternMatcher {
    final Logger logger = LoggerFactory.getLogger(BooleanPropertyMatcher.class);
    private final BooleanExpressionGuesser guesser = new BooleanExpressionGuesser();

    private <T, E extends Expression & Resolvable<T>>
    List<ASTPattern> matchNode(E node, String path, Function<T, ResolvedType> typeGetter) {
        try {
            return makeReturnPattern(node, path, () -> isBoolean(typeGetter.apply(node.resolve())));
        } catch (UnsolvedSymbolException ignore) {
        } catch (Exception e) {
            // Resolution is not possible if there is no compilation unit. Allow this case for testing
            Optional<CompilationUnit> unit = node.findCompilationUnit();
            if (unit.isPresent()) {
                // If the unit is present the error must be due to something else, so report it
                // TODO fix these errors when possible
                logger.warn(String.format("Type resolution error at line %s of %s: \"%s\"",
                        node.getRange().map(r -> String.valueOf(r.begin.line)).orElse("<no line>"),
                        unit.flatMap(CompilationUnit::getStorage).map(s -> s.getPath().toString()).orElse("<no file>"),
                        node.toString()));
            }
        }

        return makeReturnPattern(node, path, () -> guesser.guessBooleanExpression(node));
    }

    private boolean isBoolean(ResolvedType type) {
        if (type.isReferenceType()) {
            return type.asReferenceType().isAssignableBy(ResolvedPrimitiveType.BOOLEAN);
        }

        return type.equals(ResolvedPrimitiveType.BOOLEAN);
    }

    @Override
    public List<ASTPattern> visit(FieldAccessExpr n, String arg) {
        return matchNode(n, arg, ResolvedValueDeclaration::getType);
    }

    @Override
    public List<ASTPattern> visit(MethodCallExpr n, String arg) {
        return matchNode(n, arg, ResolvedMethodDeclaration::getReturnType);
    }

    @Override
    public List<ASTPattern> visit(NameExpr n, String arg) {
        if (n.getParentNode()
                .map(p -> p instanceof FieldAccessExpr)
                .orElse(false)) {
            return Collections.emptyList();
        }

        return matchNode(n, arg, ResolvedValueDeclaration::getType);
    }

    @Override
    public PatternType getPatternType() {
        return PatternType.BOOLEAN_PROPERTY;
    }
}
