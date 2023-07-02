/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro;


import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import com.raelity.antlr.ParseTreeUtil;
import com.raelity.astrolog.castro.antlr.AstroParserBaseListener;
import com.raelity.astrolog.castro.antlr.AstroParser.LvalArrayContext;
import com.raelity.astrolog.castro.antlr.AstroParser.LvalIndirectContext;
import com.raelity.astrolog.castro.antlr.AstroParser.LvalMemContext;
import com.raelity.astrolog.castro.antlr.AstroParser.Sw_nameContext;
import com.raelity.astrolog.castro.antlr.AstroParser.Switch_cmdContext;
import com.raelity.astrolog.castro.mems.AstroMem.Var;
import com.raelity.astrolog.castro.mems.Macros;
import com.raelity.astrolog.castro.mems.Registers;
import com.raelity.astrolog.castro.mems.Switches;

import static com.raelity.astrolog.castro.Util.lookup;
import static com.raelity.astrolog.castro.Util.reportError;
import static com.raelity.astrolog.castro.mems.AstroMem.Var.VarState.DUMMY;

////////////////////////////////////////////////////////////////////
// TODO: In addition to vars, check switch/macro
//       that are used have names are declared.
////////////////////////////////////////////////////////////////////

/**
 * Verification/validation pass; variables, switch commands.
 * Check that all lval's have been declared.
 * Check for well formed switch command name and switch expr usage.
 */
class Pass2 extends AstroParserBaseListener
{

static void pass2()
{
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

/** Check that only switch commands for astro expression hooks
 * have expressions. Note, this is different from {@literal {~ ... }}.
 * which is an argument to any switch command.
 */
@Override
public void exitSwitch_cmd(Switch_cmdContext ctx)
{
    String errMsg = null;
    if(ctx.name != null) {
        // check command have/not-have an expression
        if(ctx.name.tilde != null) {
            boolean isEnaDisAstroExpr
                    = isEnaDisAstroExpr(ctx.name.getText()).matches();
            if(isEnaDisAstroExpr && !ctx.bs.isEmpty())
                errMsg = "'%s' enable/disable AstroExpression does not take an expression";
            else if(!isEnaDisAstroExpr && ctx.bs.isEmpty())
                errMsg = "'%s' AstroExpression command without an expression";
        } else if(!ctx.bs.isEmpty())
            errMsg = "'%s' switch command does not take an expression";
        if(errMsg != null)
            reportError(ctx, errMsg, ParseTreeUtil.getOriginalText(
                    ctx.name, lookup(AstroParseResult.class).getInput()));
    }
}
private Matcher matcher;
private Matcher isEnaDisAstroExpr(String input)
{
    if(matcher == null) {
        Pattern p = Pattern.compile("[=_-]?~0");
        matcher = p.matcher(input);
    } else {
        matcher.reset(input);
    }
    return matcher;
}


/**
 * Check that there are no blanks in the AstroExpression command names.
 * Much simpler to do it here, rather than in the lexer/grammar.
 */
@Override
public void exitSw_name(Sw_nameContext ctx)
{
    boolean hasWS = false;
    if(ctx.pre != null) {
        if(ctx.tilde != null
                    && ctx.pre.getStopIndex() + 1 != ctx.tilde.getStartIndex()
                || ctx.tilde == null && ctx.id != null
                    && ctx.pre.getStopIndex() + 1 != ctx.id.getStartIndex())
            hasWS = true;
    }
    if(ctx.tilde != null && ctx.id != null
                && ctx.tilde.getStopIndex() + 1 != ctx.id.getStartIndex()) {
        hasWS = true;

    }
    if(hasWS)
        reportError(ctx, "'%s' whitespace in switch command name",
                    ParseTreeUtil.getOriginalText(
                            ctx, lookup(AstroParseResult.class).getInput()));
}

}
