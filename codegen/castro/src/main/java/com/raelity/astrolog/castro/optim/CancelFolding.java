/*
 * Copyright © 2023 by Ernie Rael. All rights reserved.
 */

package com.raelity.astrolog.castro.optim;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

/**
 * Can't fold, probably non-constant encountered.
 */
@SuppressWarnings("serial")
public class CancelFolding extends RuntimeException
{
public final Token token;
public final ParserRuleContext ctx;

public CancelFolding(Token token)
{
    this.token = token;
    this.ctx = null;
}

public CancelFolding(ParserRuleContext ctx)
{
    this.token = null;
    this.ctx = ctx;
}


}
