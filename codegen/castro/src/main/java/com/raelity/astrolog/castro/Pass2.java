/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro;


import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.xpath.XPath;

import com.raelity.astrolog.castro.antlr.AstroBaseListener;
import com.raelity.astrolog.castro.antlr.AstroParser.LvalArrayContext;
import com.raelity.astrolog.castro.antlr.AstroParser.LvalContext;
import com.raelity.astrolog.castro.antlr.AstroParser.LvalIndirectContext;
import com.raelity.astrolog.castro.antlr.AstroParser.LvalMemContext;
import com.raelity.astrolog.castro.mems.AstroMem.Var;
import com.raelity.astrolog.castro.mems.Macros;
import com.raelity.astrolog.castro.mems.Registers;
import com.raelity.astrolog.castro.mems.Switches;

import static com.raelity.astrolog.castro.Util.lookup;
import static com.raelity.astrolog.castro.mems.AstroMem.Var.VarState.DUMMY;

/**
 *
 * @author err
 */
class Pass2 extends AstroBaseListener
{
static void pass2()
{
    if(Boolean.TRUE)
        new Pass2().useXpath();
    else
        new Pass2().useListener();
}

private final Registers registers;
private final Macros macros;
private final Switches switches;

private Pass2()
{
    this.registers = lookup(Registers.class);
    this.macros = lookup(Macros.class);
    this.switches = lookup(Switches.class);
}

void useXpath()
{
    AstroParseResult apr = lookup(AstroParseResult.class);
    for(ParseTree tree : XPath.findAll(apr.getProgram(), "//lval", apr.getParser()))
        checkReportUnknownVar(((LvalContext)tree).id);
    
}

/** Check that the referenced variable has been declared;
 * if not then report an error and give it a dummy declaration
 * to avoid further errors on the name.
 */
private void checkReportUnknownVar(Token token)
{
    Var var = registers.getVar(token.getText());
    if(var != null)
        return;
    Util.reportError(token, "unknown variable '%s' (first occurance)",
                     token.getText());
    registers.declare(token, 1, -1, DUMMY);
}

void useListener()
{
    AstroParseResult apr = lookup(AstroParseResult.class);
    ParseTreeWalker walker = new ParseTreeWalker();
    walker.walk(this, apr.getProgram());
}

@Override
public void exitLvalMem(LvalMemContext ctx)
{
    checkReportUnknownVar(ctx.Identifier().getSymbol());
}

@Override
public void exitLvalIndirect(LvalIndirectContext ctx)
{
    checkReportUnknownVar(ctx.Identifier().getSymbol());
}

@Override
public void exitLvalArray(LvalArrayContext ctx)
{
    checkReportUnknownVar(ctx.Identifier().getSymbol());
}

}
