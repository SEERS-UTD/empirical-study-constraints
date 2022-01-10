package edu.utdallas.seers.lasso.detector;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.Type;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;

public class BooleanPropertyMatcher implements PatternMatcher {
    private static final BooleanExpressionChecker BOOLEAN_EXPRESSION_CHECKER;

    /**
     * Tries to establish whether a given node can be discarded as a boolean expression, for
     * example, if the node is not of expression type or it uses an operator only valid on integer.
     * <p>
     * A result of {@code false} guarantees that the node cannot be a boolean expression, but the
     * opposite cannot be assumed.
     *
     * @param node Node to check for boolean expression.
     * @return {@code false} if the node cannot possibly be a boolean expression and
     * {@code true} otherwise.
     */
    public static boolean match(Node node) {
        Expression expression = node instanceof Expression ? ((Expression) node) : null;

        if (expression == null) return false;

        boolean validType = expression.isNameExpr() || expression.isMethodCallExpr() || expression.isFieldAccessExpr();

        if (!validType) return false;

        // TODO explore combining JavaParser directory + jar resolvers for this part
        return isBooleanExpression(expression);
    }

    private static boolean isBooleanExpression(Expression expression) {
        Node parentNode = expression.getParentNode()
                .orElseThrow(() -> new RuntimeException("This node should have a parent"));

        Expression parentExpression = parentNode instanceof Expression ? ((Expression) parentNode) : null;

        if (parentExpression == null) {
            /*
             Parent is not an expression, e.g. an if statement. We assume it is a boolean
             expression even though it is not guaranteed, e.g. return getInt();

             Narrowing this down will require some type resolution.

             TODO we can filter a few more here e.g. for statement
            */
            return true;
        }

        /*
        List of expression subtypes and whether they are valid
            ArrayInitializerExpr    YES
            ConditionalExpr    YES
            SingleMemberAnnotationExpr    YES
            NameExpr    YES but only at first
            FieldAccessExpr    YES but only if boxed boolean
            ObjectCreationExpr    YES but scope only if boxed
            MethodCallExpr    YES scope only if bool
            UnaryExpr    IF binary negation
            BinaryExpr    IF boolean op
            ArrayAccessExpr    IF e is name
            AssignExpr    IF e is value
            EnclosedExpr    IF parent checks out
            CastExpr    IF type boolean
            AnnotationExpr    NO
            ArrayCreationExpr    NO
            BooleanLiteralExpr    NO
            CharLiteralExpr    NO
            ClassExpr    NO
            DoubleLiteralExpr    NO
            InstanceOfExpr    NO
            IntegerLiteralExpr    NO
            LambdaExpr    NO
            LiteralExpr    NO
            LiteralStringValueExpr    NO
            LongLiteralExpr    NO
            MarkerAnnotationExpr    NO
            MethodReferenceExpr    NO
            NormalAnnotationExpr    NO
            NullLiteralExpr    NO
            StringLiteralExpr    NO
            SuperExpr    NO
            SwitchExpr    NO
            TextBlockLiteralExpr    NO
            ThisExpr    NO
            TypeExpr    NO
            VariableDeclarationExpr    NO
         */

        // Cannot directly discard it, so recursively check parent
        if (parentExpression.isEnclosedExpr()) return isBooleanExpression(parentExpression);

        return Optional.ofNullable(BOOLEAN_EXPRESSION_CHECKER.get(parentExpression.getClass()))
                .map(c -> c.test(parentExpression, expression))
                .orElse(false);
    }

    // Initialize checkers indexed by statement class
    static {
        // Intentionally excludes == and =! because those cases are binary comparison instead
        HashSet<BinaryExpr.Operator> booleanOperators = new HashSet<>(Arrays.asList(
                BinaryExpr.Operator.AND, BinaryExpr.Operator.OR
        ));

        BOOLEAN_EXPRESSION_CHECKER = new BooleanExpressionChecker();

        BOOLEAN_EXPRESSION_CHECKER
                .check(ArrayAccessExpr.class, Expression::asArrayAccessExpr,
                        (p, e) -> p.getName().equals(e))
                .check(UnaryExpr.class, Expression::asUnaryExpr,
                        (p, e) -> p.getOperator().equals(UnaryExpr.Operator.LOGICAL_COMPLEMENT))
                .check(AssignExpr.class, Expression::asAssignExpr,
                        (p, e) -> p.getValue().equals(e))
                .check(BinaryExpr.class, Expression::asBinaryExpr,
                        (p, e) -> booleanOperators.contains(p.getOperator()))
                .check(CastExpr.class, Expression::asCastExpr,
                        BooleanPropertyMatcher::isBooleanExpression);

        BOOLEAN_EXPRESSION_CHECKER
                .always(ArrayInitializerExpr.class)
                .always(ConditionalExpr.class)
                .always(SingleMemberAnnotationExpr.class)
                // TODO for the 'scope' field these are only valid if it's a boxed Boolean
                .always(FieldAccessExpr.class)
                .always(ObjectCreationExpr.class)
                .always(MethodCallExpr.class);
    }

    private static boolean isBooleanExpression(CastExpr parent, Expression e) {
        Type castType = parent.getType();
        PrimitiveType primitiveType = castType instanceof PrimitiveType ? ((PrimitiveType) castType) : null;

        if (primitiveType != null) {
            return primitiveType.equals(PrimitiveType.booleanType());
        }

        // TODO this could be an error in the unlikely case that someone has called their class "Boolean"
        return castType instanceof ClassOrInterfaceType &&
                ((ClassOrInterfaceType) castType).getName().toString().equals("Boolean");
    }

    /**
     * Map from expression type to predicate that checks if the current expression can be a boolean
     * expression. The key is the type of the parent expression.
     */
    static class BooleanExpressionChecker {
        private final Map<Class<? extends Expression>, Checker> checkers = new HashMap<>();
        private final Checker alwaysTrue = (p, e) -> true;

        private <T extends Expression> BooleanExpressionChecker check(
                Class<T> parentClass,
                Function<Expression, T> parentConverter,
                BiPredicate<T, Expression> checker
        ) {
            checkers.put(parentClass, (p, e) -> checker.test(parentConverter.apply(p), e));

            return this;
        }

        public BooleanExpressionChecker always(Class<? extends Expression> parentClass) {
            checkers.put(parentClass, alwaysTrue);

            return this;
        }

        public Checker get(Class<? extends Expression> clazz) {
            return checkers.get(clazz);
        }

        /**
         * First argument is parent and second is the target expression.
         */
        interface Checker extends BiPredicate<Expression, Expression> {
        }
    }
}
