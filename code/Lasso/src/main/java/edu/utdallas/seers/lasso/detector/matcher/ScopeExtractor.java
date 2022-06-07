package edu.utdallas.seers.lasso.detector.matcher;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.GenericVisitorWithDefaults;

import java.util.Optional;

class ScopeExtractor extends GenericVisitorWithDefaults<Optional<Expression>, Void> {
    @Override
    public Optional<Expression> visit(MethodCallExpr n, Void arg) {
        return n.getScope();
    }

    @Override
    public Optional<Expression> visit(FieldAccessExpr n, Void arg) {
        return Optional.of(n.getScope());
    }

    @Override
    public Optional<Expression> visit(NameExpr n, Void arg) {
        return Optional.of(n);
    }

    @Override
    public Optional<Expression> defaultAction(Node n, Void arg) {
        return Optional.empty();
    }

    @Override
    public Optional<Expression> defaultAction(NodeList n, Void arg) {
        return super.defaultAction((Node) null, arg);
    }
}
