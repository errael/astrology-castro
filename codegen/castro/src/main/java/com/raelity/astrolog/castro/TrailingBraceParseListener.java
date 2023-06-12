/*
 * Portions created by Ernie Rael are
 * Copyright (C) 2023 Ernie Rael.  All Rights Reserved.
 *
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * The Original Code is jvi - vi editor clone.
 *
 * Contributor(s): Ernie Rael <errael@raelity.com>
 */

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
