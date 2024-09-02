package com.ishland.c2me.opts.dfc.common.ast.binary;

import com.ishland.c2me.opts.dfc.common.ast.AstNode;
import com.ishland.c2me.opts.dfc.common.ast.EvalType;

public class MaxNode extends AbstractBinaryNode { // missed optimization: left > right.maxValue

    public MaxNode(AstNode left, AstNode right) {
        super(left, right);
    }

    @Override
    protected AstNode newInstance(AstNode left, AstNode right) {
        return new MaxNode(left, right);
    }

    @Override
    public double evalSingle(int x, int y, int z, EvalType type) {
        return Math.max(this.left.evalSingle(x, y, z, type), this.right.evalSingle(x, y, z, type));
    }

    @Override
    public void evalMulti(double[] res, int[] x, int[] y, int[] z, EvalType type) {
        double[] res1 = new double[res.length];
        this.left.evalMulti(res, x, y, z, type);
        this.right.evalMulti(res1, x, y, z, type);
        for (int i = 0; i < res1.length; i++) {
            res[i] = Math.max(res[i], res1[i]);
        }
    }
}
