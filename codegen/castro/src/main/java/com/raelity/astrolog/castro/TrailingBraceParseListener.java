/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.raelity.astrolog.castro.antlr.AstroBaseListener;
import com.raelity.astrolog.castro.antlr.AstroParser.ExprContext;

/**
 *
 * @author err
 */
class TrailingBraceParseListener extends AstroBaseListener
{
    @Override
    public void visitTerminal(TerminalNode tn)
    {
    }

    @Override
    public void visitErrorNode(ErrorNode en)
    {
    }

    @Override
    public void enterEveryRule(ParserRuleContext prc)
    {
    }

    @Override
    public void exitEveryRule(ParserRuleContext ctx)
    {
        if(ctx instanceof ExprContext expr)
            expr.fBlock = expr.getText().endsWith("}") ? 1 : 0;
    }
    
}
