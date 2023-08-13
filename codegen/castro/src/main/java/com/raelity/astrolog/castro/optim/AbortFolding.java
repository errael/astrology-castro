/*
 * Copyright © 2023 by Ernie Rael. All rights reserved.
 */

package com.raelity.astrolog.castro.optim;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

/**
 * Something wrong happened during folding.
 * Like visiting a node that shouldn't be visited.
 */
@SuppressWarnings("serial")
public class AbortFolding extends RuntimeException
{
public final Token token;
public final ParserRuleContext ctx;

public AbortFolding(String msg)
{
    super(msg);
    this.token = null;
    this.ctx = null;
}

public AbortFolding(ParserRuleContext ctx)
{
    this.token = null;
    this.ctx = ctx;
}

public AbortFolding(Token token)
{
    this.token = token;
    this.ctx = null;
}

}
