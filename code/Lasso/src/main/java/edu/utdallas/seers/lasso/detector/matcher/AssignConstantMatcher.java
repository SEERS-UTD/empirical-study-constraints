package edu.utdallas.seers.lasso.detector.matcher;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.visitor.GenericVisitorWithDefaults;
import com.github.javaparser.resolution.declarations.*;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserSymbolDeclaration;
import edu.utdallas.seers.lasso.entity.ASTPattern;
import edu.utdallas.seers.lasso.entity.NameValueASTPattern;
import edu.utdallas.seers.lasso.entity.PatternType;
import edu.utdallas.seers.lasso.entity.variables.Field;
import edu.utdallas.seers.lasso.entity.variables.LocalVariable;
import edu.utdallas.seers.lasso.entity.variables.Method;
import edu.utdallas.seers.lasso.entity.variables.Variable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * TODO perhaps we want to allow operations that contain only literals such as 2 * 2
 * Accepting static constants makes sense
 */
public class AssignConstantMatcher extends PatternMatcher {

    private final Logger logger = LoggerFactory.getLogger(AssignConstantMatcher.class);
    private final ConstantExtractor constantExtractor = new ConstantExtractor();
    private final VariableExtractor variableExtractor = new VariableExtractor();

    @Override
    public List<ASTPattern> visit(VariableDeclarator n, String arg) {
        return n.getInitializer()
                .flatMap(e -> constantExtractor.extractConstant(e, true))
                .<ASTPattern>map(c -> {
                    try {
                        return new NameValueASTPattern(
                                getPatternType(),
                                arg,
                                nodeToLines(n),
                                c,
                                variableExtractor.extractVariable(n)
                        );
                    } catch (Exception e) {
                        // TODO this can fail if the expression is in a method overridden in an enum constant
                        logger.error("Could not extract variable", e);
                        return null;
                    }
                })
                .map(Collections::singletonList)
                .orElse(Collections.emptyList());
    }

    @Override
    public List<ASTPattern> visit(AssignExpr n, String arg) {
        return constantExtractor.extractConstant(n.getValue(), true)
                .<ASTPattern>flatMap(c -> {
                    try {
                        return variableExtractor.extractForTarget(n.getTarget())
                                .map(v -> new NameValueASTPattern(
                                        getPatternType(),
                                        arg,
                                        nodeToLines(n),
                                        c,
                                        v
                                ));
                    } catch (Exception e) {
                        // TODO this can fail if the expression is in a method overridden in an enum constant
                        logger.error("Could not extract variable", e);
                        return Optional.empty();
                    }
                })
                .map(Collections::singletonList)
                .orElse(Collections.emptyList());
    }

    @Override
    public PatternType getPatternType() {
        return PatternType.ASSIGN_CONSTANT;
    }

    /**
     * Finds the variable for a constant assignment, e.g. fully-qualified field name or variable name +
     * fully qualified method name.
     */
    private static class VariableExtractor extends GenericVisitorWithDefaults<Variable, String> {

        Variable extractVariable(VariableDeclarator declarator) {
            return extractFromResolved(declarator, declarator.resolve())
                    .orElseThrow(() -> new IllegalStateException("Should find variable"));
        }

        Optional<Variable> extractForTarget(Expression target) {
            // Target of an assignment expression

            ResolvedValueDeclaration declaration;
            try {
                // TODO target could be a combination of enclosed expr and field access
                if (target.isNameExpr()) {
                    declaration = target.asNameExpr().resolve();
                } else if (target.isFieldAccessExpr()) {
                    declaration = target.asFieldAccessExpr().resolve();
                } else {
                    return Optional.empty();
                }
            } catch (Exception ignore) {
                // TODO reevaluate this after figuring out resolution from libraries and exclusion of tests (see ASTPatternDetector)
                return Optional.empty();
            }

            return extractFromResolved(target, declaration);
        }

        private Optional<Variable> extractFromResolved(Node node, ResolvedValueDeclaration declaration) {
            if (declaration.isField()) {
                ResolvedFieldDeclaration fieldDeclaration = declaration.asField();
                ResolvedTypeDeclaration type = fieldDeclaration.declaringType();

                return Optional.of(new Field(type.getQualifiedName(), fieldDeclaration.getName()));
            } else if (declaration.isParameter() || declaration.isVariable() ||
                    // FIXME JavaParser bug, isVariable should return true in this case
                    (declaration.getClass().equals(JavaParserSymbolDeclaration.class) &&
                            ((JavaParserSymbolDeclaration) declaration).getWrappedNode() instanceof VariableDeclarator)) {
                // We consider these local variables so just check parents as normal
                // We need to check parents because the resolved parameters and variables don't have a lot of info
                return Optional.of(node.accept(this, declaration.getName()));
            }

            return Optional.empty();
        }

        @Override
        public Variable visit(MethodDeclaration n, String arg) {
            ResolvedMethodDeclaration declaration = n.resolve();

            return new LocalVariable(
                    declaration.declaringType().getQualifiedName(),
                    declaration.getName(),
                    arg
            );
        }

        @Override
        public Variable visit(ConstructorDeclaration n, String arg) {
            ResolvedConstructorDeclaration declaration = n.resolve();

            return new LocalVariable(
                    declaration.declaringType().getQualifiedName(),
                    Method.CONSTRUCTOR_NAME,
                    arg
            );
        }

        @Override
        public Variable defaultAction(Node n, String arg) {
            return n.getParentNode()
                    // FIXME we are missing initializer blocks
                    .orElseThrow(() -> new IllegalStateException("Should have found suitable parent"))
                    .accept(this, arg);
        }

        @Override
        public Variable defaultAction(NodeList n, String arg) {
            return super.defaultAction((Node) null, null);
        }
    }
}
