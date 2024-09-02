package com.ishland.c2me.opts.dfc.common.ast.unary;

import com.ishland.c2me.opts.dfc.common.AstTransformer;
import com.ishland.c2me.opts.dfc.common.ast.AstNode;

import java.util.Objects;

public abstract class AbstractUnaryNode implements AstNode {

    protected final AstNode operand;

    public AbstractUnaryNode(AstNode operand) {
        this.operand = Objects.requireNonNull(operand);
    }

    @Override
    public AstNode[] getChildren() {
        return new AstNode[]{operand};
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AbstractUnaryNode that = (AbstractUnaryNode) o;
        return Objects.equals(operand, that.operand);
    }

    @Override
    public int hashCode() {
        int result = 1;

        Object o = this.getClass();
        result = 31 * result + o.hashCode();
        result = 31 * result + operand.hashCode();

        return result;
    }

    protected abstract AstNode newInstance(AstNode operand);

    @Override
    public AstNode transform(AstTransformer transformer) {
        AstNode operand = this.operand.transform(transformer);
        if (this.operand == operand) {
            return transformer.transform(this);
        } else {
            return transformer.transform(newInstance(operand));
        }
    }
}
