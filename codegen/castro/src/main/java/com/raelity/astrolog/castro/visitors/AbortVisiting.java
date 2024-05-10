/*
 * Copyright © 2023 by Ernie Rael. All rights reserved.
 */

package com.raelity.astrolog.castro.visitors;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

/**
 * Something wrong happened during folding.
 * Like visiting a node that shouldn't be visited.
 */
@SuppressWarnings("serial")
public class AbortVisiting extends RuntimeException
{
public final Token token;
public final ParserRuleContext ctx;

/**
 * See Visitor.
 * This constructor is for the unimplemented ops that should not be used.
 */
public AbortVisiting()
{
    this("No trespassing.");
}

public AbortVisiting(String msg)
{
    super(msg);
    this.token = null;
    this.ctx = null;
}

public AbortVisiting(ParserRuleContext ctx)
{
    this.token = null;
    this.ctx = ctx;
}

public AbortVisiting(Token token)
{
    this.token = token;
    this.ctx = null;
}

}
