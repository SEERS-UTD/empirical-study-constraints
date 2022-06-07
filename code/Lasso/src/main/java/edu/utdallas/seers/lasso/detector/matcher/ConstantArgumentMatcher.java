package edu.utdallas.seers.lasso.detector.matcher;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithArguments;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.resolution.Resolvable;
import edu.utdallas.seers.lasso.entity.ASTPattern;
import edu.utdallas.seers.lasso.entity.NameValueASTPattern;
import edu.utdallas.seers.lasso.entity.PatternType;
import edu.utdallas.seers.lasso.entity.constants.Constant;
import edu.utdallas.seers.lasso.entity.variables.Method;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ConstantArgumentMatcher extends PatternMatcher {

    private final ConstantExtractor constantExtractor = new ConstantExtractor();

    private <T extends Node & NodeWithArguments<?> & Resolvable<U>, U>
    List<ASTPattern> extractInstances(T callable, String fileName, Function<U, Method> variableExtractor) {
        List<Constant<?>> constants = callable.getArguments().stream()
                .map(e -> constantExtractor.extractConstant(e, true))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .distinct()
                .collect(Collectors.toList());

        if (constants.isEmpty()) {
            return Collections.emptyList();
        }

        Method method;
        try {
            method = variableExtractor.apply(callable.resolve());
        } catch (Exception ignore) {
            // TODO reevaluate this after figuring out resolution from libraries and exclusion of tests (see ASTPatternDetector)
            return Collections.emptyList();
        }

        return constants.stream()
                .map(c -> new NameValueASTPattern(
                        PatternType.CONSTANT_ARGUMENT,
                        fileName,
                        nodeToLines(callable),
                        c,
                        method
                ))
                .collect(Collectors.toList());
    }

    @Override
    public List<ASTPattern> visit(ObjectCreationExpr n, String arg) {
        return extractInstances(n, arg, r -> new Method(r.declaringType().getQualifiedName(), Method.CONSTRUCTOR_NAME));
    }

    @Override
    public List<ASTPattern> visit(MethodCallExpr n, String arg) {
        return extractInstances(n, arg, r -> new Method(r.declaringType().getQualifiedName(), r.getName()));
    }

    @Override
    public List<ASTPattern> visit(EnumConstantDeclaration n, String arg) {
        // TODO
        return super.visit(n, arg);
    }

    @Override
    public List<ASTPattern> visit(ExplicitConstructorInvocationStmt n, String arg) {
        return extractInstances(n, arg, r -> new Method(r.declaringType().getQualifiedName(), Method.CONSTRUCTOR_NAME));
    }

    @Override
    public PatternType getPatternType() {
        return PatternType.CONSTANT_ARGUMENT;
    }
}
