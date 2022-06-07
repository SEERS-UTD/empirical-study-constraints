package edu.utdallas.seers.lasso.detector.matcher;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.Type;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;

// TODO turn into visitor
class BooleanExpressionGuesser {
    private final BooleanExpressionChecker booleanExpressionChecker;

    // Initialize checkers indexed by statement class
    {
        // Intentionally excludes == and =! because those cases are binary comparison instead
        HashSet<BinaryExpr.Operator> booleanOperators = new HashSet<>(Arrays.asList(
                BinaryExpr.Operator.AND, BinaryExpr.Operator.OR
        ));

        booleanExpressionChecker = new BooleanExpressionChecker();

        booleanExpressionChecker
                .check(ArrayAccessExpr.class, Expression::asArrayAccessExpr,
                        (p, e) -> p.getName().equals(e))
                .check(UnaryExpr.class, Expression::asUnaryExpr,
                        (p, e) -> p.getOperator().equals(UnaryExpr.Operator.LOGICAL_COMPLEMENT))
                .check(AssignExpr.class, Expression::asAssignExpr,
                        (p, e) -> p.getValue().equals(e))
                .check(BinaryExpr.class, Expression::asBinaryExpr,
                        (p, e) -> booleanOperators.contains(p.getOperator()))
                .check(CastExpr.class, Expression::asCastExpr,
                        this::isBooleanExpression);

        booleanExpressionChecker
                .always(ArrayInitializerExpr.class)
                .always(ConditionalExpr.class)
                .always(SingleMemberAnnotationExpr.class)
                // TODO for the 'scope' field these are only valid if it's a boxed Boolean
                .always(FieldAccessExpr.class)
                .always(ObjectCreationExpr.class)
                .always(MethodCallExpr.class);
    }

    BooleanExpressionGuesser() {
    }

    /**
     * Tries to guess if an expression can be a boolean expression. True does not guarantee that
     * it can be.
     *
     * @param expression Target.
     * @return {@code false} if the expression cannot be a boolean expression, {@code true} if it
     * could possibly be.
     */
    boolean guessBooleanExpression(Expression expression) {
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
        if (parentExpression.isEnclosedExpr()) return guessBooleanExpression(parentExpression);

        return Optional.ofNullable(booleanExpressionChecker.get(parentExpression.getClass()))
                .map(c -> c.test(parentExpression, expression))
                .orElse(false);
    }

    private boolean isBooleanExpression(CastExpr parent, Expression e) {
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
    private static class BooleanExpressionChecker {
        private final Map<Class<? extends Expression>, Checker> checkers = new HashMap<>();
        private final BooleanExpressionChecker.Checker alwaysTrue = (p, e) -> true;

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

        public BooleanExpressionChecker.Checker get(Class<? extends Expression> clazz) {
            return checkers.get(clazz);
        }

        /**
         * First argument is parent and second is the target expression.
         */
        interface Checker extends BiPredicate<Expression, Expression> {
        }
    }
}