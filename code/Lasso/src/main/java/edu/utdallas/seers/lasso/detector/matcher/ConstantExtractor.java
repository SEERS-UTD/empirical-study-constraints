package edu.utdallas.seers.lasso.detector.matcher;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.GenericVisitorWithDefaults;
import com.github.javaparser.resolution.Resolvable;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserFieldDeclaration;
import edu.utdallas.seers.lasso.entity.constants.*;

import java.math.BigInteger;
import java.util.Optional;
import java.util.function.Function;

/**
 * TODO should we handle null literal?
 * TODO must handle values like Int.MAX_VALUE
 */
public class ConstantExtractor extends GenericVisitorWithDefaults<Optional<Constant<?>>, Boolean> {
    /**
     * Checks if the expression is a literal. Accepts cases such as {@code (5)}, {@code -5},
     * {@code ((-5))}, etc.
     *
     * @param expression  Expression to evaluate.
     * @param allowStatic Treat static final fields as constants.
     * @return {@code true} if the expression is a literal or consists only of one literal.
     */
    public Optional<Constant<?>> extractConstant(Expression expression, boolean allowStatic) {
        return expression.accept(this, allowStatic);
    }

    private <T extends Resolvable<ResolvedValueDeclaration>>
    Optional<Constant<?>> extractFromStatic(T expression) {
        ResolvedValueDeclaration declaration = expression.resolve();

        JavaParserFieldDeclaration fieldDeclaration =
                declaration instanceof JavaParserFieldDeclaration ?
                        ((JavaParserFieldDeclaration) declaration) : null;

        // TODO might be able to handle ReflectionFieldDeclaration
        if (fieldDeclaration == null) {
            return Optional.empty();
        }

        Optional<Constant<?>> literalConstant = fieldDeclaration.getVariableDeclarator()
                .getInitializer()
                // Allowing static here could cause many chained calls. Enable if necessary
                .flatMap(e -> extractConstant(e, false));

        if (literalConstant.isPresent()) {
            return literalConstant;
        }

        // Even if we didn't find a literal, if the field is static and final, we will consider it constant
        FieldDeclaration declarationNode = fieldDeclaration.getWrappedNode();

        if (fieldDeclaration.getType().isReferenceType() &&
                declarationNode.hasModifier(Modifier.Keyword.STATIC) &&
                declarationNode.hasModifier(Modifier.Keyword.FINAL)) {
            return Optional.of(new ObjectConstant(
                    fieldDeclaration.declaringType().getQualifiedName(),
                    fieldDeclaration.getName()
            ));
        }

        return Optional.empty();
    }

    @Override
    public Optional<Constant<?>> visit(BooleanLiteralExpr n, Boolean arg) {
        return Optional.of(new BooleanConstant(n.getValue()));
    }

    @Override
    public Optional<Constant<?>> visit(CharLiteralExpr n, Boolean arg) {
        return Optional.of(new CharConstant(n.asChar()));
    }

    @Override
    public Optional<Constant<?>> visit(DoubleLiteralExpr n, Boolean arg) {
        return Optional.of(new DoubleConstant(n.asDouble()));
    }

    @Override
    public Optional<Constant<?>> visit(IntegerLiteralExpr n, Boolean arg) {
        Number value = n.asNumber();

        // See IntegerLiteral asNumber
        if (value instanceof Long) {
            return Optional.of(new IntegerMinValue());
        }

        return Optional.of(new IntegerConstant((int) value));
    }

    @Override
    public Optional<Constant<?>> visit(LongLiteralExpr n, Boolean arg) {
        Number value = n.asNumber();

        if (value instanceof BigInteger) {
            return Optional.of(new LongMinValue());
        }

        return Optional.of(new LongConstant((long) value));
    }

    @Override
    public Optional<Constant<?>> visit(StringLiteralExpr n, Boolean arg) {
        return Optional.of(new StringConstant(n.asString()));
    }

    @Override
    public Optional<Constant<?>> visit(EnclosedExpr n, Boolean arg) {
        return n.getInner().accept(this, null);
    }

    @Override
    public Optional<Constant<?>> visit(UnaryExpr n, Boolean arg) {
        UnaryExpr.Operator operator = n.getOperator();
        Optional<Constant<?>> innerResult = n.getExpression().accept(this, null);

        // See IntegerLiteral asNumber
        if (innerResult.isPresent() && innerResult.get() instanceof IntegerMinValue) {
            assert n.getOperator().equals(UnaryExpr.Operator.MINUS);
            return Optional.of(new IntegerConstant(Integer.MIN_VALUE));
        }

        if (innerResult.isPresent() && innerResult.get() instanceof LongMinValue) {
            assert n.getOperator().equals(UnaryExpr.Operator.MINUS);
            return Optional.of(new LongConstant(Long.MIN_VALUE));
        }

        Function<Constant<?>, Constant<?>> function;

        switch (operator) {
            case MINUS:
                function = Constant::numNegate;
                break;
            case PREFIX_DECREMENT:
            case POSTFIX_DECREMENT:
                function = Constant::decrement;
                break;
            case PREFIX_INCREMENT:
            case POSTFIX_INCREMENT:
                function = Constant::increment;
                break;
            case LOGICAL_COMPLEMENT:
                function = Constant::boolNegate;
                break;
            case BITWISE_COMPLEMENT:
                function = Constant::bitComplement;
                break;
            case PLUS:
                function = Function.identity();
                break;
            default:
                throw new IllegalArgumentException("Unknown unary operator: " + operator);
        }

        return innerResult.map(function);
    }

    // TODO we could also accept a method call if it is a getter
    @Override
    public Optional<Constant<?>> visit(FieldAccessExpr n, Boolean arg) {
        if (arg) {
            return extractFromStatic(n);
        }

        return Optional.empty();
    }

    @Override
    public Optional<Constant<?>> visit(NameExpr n, Boolean arg) {
        if (arg) {
            return extractFromStatic(n);
        }

        return Optional.empty();
    }

    @Override
    public Optional<Constant<?>> defaultAction(Node n, Boolean arg) {
        return Optional.empty();
    }

    @Override
    public Optional<Constant<?>> defaultAction(NodeList n, Boolean arg) {
        return Optional.empty();
    }

    static class IntegerMinValue extends Constant<Object> {
        protected IntegerMinValue() {
            super(null, null);
        }
    }

    static class LongMinValue extends Constant<Object> {
        protected LongMinValue() {
            super(null, null);
        }
    }
}
