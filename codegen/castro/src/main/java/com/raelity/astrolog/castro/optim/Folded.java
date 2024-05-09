/*
 * Copyright © 2023 by Ernie Rael. All rights reserved.
 */

package com.raelity.astrolog.castro.optim;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

import com.raelity.astrolog.castro.Util;

/**
 * Immutable constant value.
 * When an expression is encountered that is not constant,
 * create a fold with the the ctx/token of the error.
 * 
 * Might extend with optype and/or precedence if want to get fancy.
 */
public class Folded
{
private final long v;

static Folded get(long v)
{
    if(!Util.isOverflow(v))
        return new Folded(v);
    else
        return new OverflowFolded(v);
}

static Folded get(boolean boolval)
{
    return new Folded(boolval ? 1 : 0);
}

/** Not constant: NotFolded */
static Folded get(ParserRuleContext ctx)
{
    return new NotFolded(ctx);
}

/** Not constant: NotFolded */
static Folded get(Token token)
{
    return new NotFolded(token);
}

Folded(long v)
{
    this.v = v;
}

/** only used to get the final result. */
int val() {
    return (int)v;
}

/** Used to get intermediate results. */
long lval() {
    return v;
}

/** Used to get result as a boolean. */
boolean boolval() {
    return v != 0;
}

/** @return true if a valid constant, overflow is not valid. */
boolean isConstant() {
    return true;
}

/** @return if this is an overflow record */
boolean isOverflow()
{
    return false;
}

/** @return if this is a variable record */
boolean isVariable()
{
    return false;
}

ParserRuleContext ctx()
{
    throw new IllegalStateException();
}

Token token()
{
    throw new IllegalStateException();
}

@Override
public String toString()
{
    return "Folded{" + "l=" + v + '}';
}


    /**
     * A non-constant involed in folding,
     * represent a variable part of an expresstion.
     */
    private static class NotFolded extends Folded
    {
        final ParserRuleContext ctx;
        final Token token;

        NotFolded(ParserRuleContext ctx)
        {
            super(Long.MIN_VALUE);
            this.ctx = ctx;
            token = null;
        }

        NotFolded(Token token)
        {
            super(Long.MIN_VALUE);
            ctx = null;
            this.token = token;
        }

        @Override
        int val()
        {
            throw new IllegalStateException();
        }

        @Override
        boolean isConstant()
        {
            return false;
        }

        @Override
        boolean isOverflow()
        {
            return false;
        }

        @Override
        boolean isVariable()
        {
            return true;
        }

        @Override
        ParserRuleContext ctx()
        {
            return ctx;
        }

        @Override
        Token token()
        {
            return token;
        }
    }

    /**
     * Overflow constant.
     */
    private static class OverflowFolded extends Folded
    {
        OverflowFolded(long v)
        {
            super(v);
        }

        @Override
        int val()
        {
            throw new IllegalStateException();
        }

        @Override
        boolean isOverflow()
        {
            return true;
        }

        @Override
        boolean isConstant()
        {
            return false;
        }

        @Override
        boolean isVariable()
        {
            return false;
        }
    }

}
