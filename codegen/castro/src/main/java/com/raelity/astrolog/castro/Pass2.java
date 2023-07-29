/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro;


import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import com.raelity.antlr.ParseTreeUtil;
import com.raelity.astrolog.castro.antlr.AstroParser.Func_callContext;
import com.raelity.astrolog.castro.antlr.AstroParserBaseListener;
import com.raelity.astrolog.castro.antlr.AstroParser.LvalArrayContext;
import com.raelity.astrolog.castro.antlr.AstroParser.LvalContext;
import com.raelity.astrolog.castro.antlr.AstroParser.LvalIndirectContext;
import com.raelity.astrolog.castro.antlr.AstroParser.LvalMemContext;
import com.raelity.astrolog.castro.antlr.AstroParser.Sw_nameContext;
import com.raelity.astrolog.castro.antlr.AstroParser.Switch_cmdContext;
import com.raelity.astrolog.castro.mems.AstroMem.Var;
import com.raelity.astrolog.castro.mems.Registers;
import com.raelity.astrolog.castro.tables.Functions;
import com.raelity.astrolog.castro.tables.Functions.Function;

import static com.raelity.astrolog.castro.Constants.isConstant;
import static com.raelity.astrolog.castro.Util.lookup;
import static com.raelity.astrolog.castro.Util.lvalArg2Func;
import static com.raelity.astrolog.castro.Util.reportError;
import static com.raelity.astrolog.castro.mems.AstroMem.Var.VarState.DUMMY;
import static com.raelity.astrolog.castro.Util.getText;
import static com.raelity.astrolog.castro.antlr.AstroLexer.Tilde;

////////////////////////////////////////////////////////////////////
// TODO: In addition to vars, check switch/macro
//       that are used have names are declared.
////////////////////////////////////////////////////////////////////

/**
 * Verification/validation pass; variables, switch commands. <br>
 * Check that all lval's have been declared. <br>
 * Check for well formed switch command name and switch expr usage. <br>
 * In expr, switch(), macro() function calls either have a switch/macro
 * name or a general expression. <br>
 */
class Pass2 extends AstroParserBaseListener
{

static void pass2()
{
    new Pass2().useListener();
}

private final AstroParseResult apr;

private final Registers registers;

private Pass2()
{
    this.apr = lookup(AstroParseResult.class);
    this.registers = lookup(Registers.class);
}

/** Check that the referenced variable has been declared;
 * if not then report an error and give it a dummy declaration
 * to avoid further errors on the name.
 */
private void checkReportUnknownVar(LvalContext ctx, Token token)
{
    Var var = registers.getVar(token.getText());
    if(var != null)
        return;
    Func_callContext fc_ctx = lvalArg2Func(ctx);
    if(fc_ctx != null && isDoneReportSpecialFuncArgs(fc_ctx))
        return;
    if(isConstant(token)) {
        if(!(ctx instanceof LvalMemContext))
            reportError(token, "constant '%s' used as a variable", token.getText());
        return;
    } else
        reportError(token, "unknown variable '%s' (first occurance)",
                    token.getText());
    registers.declare(token, 1, -1, DUMMY);
}

void useListener()
{
    ParseTreeWalker walker = new ParseTreeWalker();
    walker.walk(this, apr.getProgram());
}

@Override
public void exitLvalMem(LvalMemContext ctx)
{
    checkReportUnknownVar(ctx, ctx.Identifier().getSymbol());
}

@Override
public void exitLvalIndirect(LvalIndirectContext ctx)
{
    checkReportUnknownVar(ctx, ctx.Identifier().getSymbol());
}

@Override
public void exitLvalArray(LvalArrayContext ctx)
{
    checkReportUnknownVar(ctx, ctx.Identifier().getSymbol());
    Integer constVal = Util.expr2constInt(ctx.idx);
    // If var's null, should have already been an error
    Var var = registers.getVar(ctx.id.getText());
    if(var == null && !apr.hasError()) {
        reportError(ctx, "internal error: exitLvalArray: no error, expecting one");
    }
    if(constVal != null && var != null) {
        if(constVal >= var.getSize())
            reportError(Error.ARRAY_OOB, ctx, "'%s' array index out of bounds", ctx.getText());
    }
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
        Object optParam = null;
        if(ctx.name.tilde != null) {
            // It's a "~" command, like "~j0" or "_~j0" or ...
            boolean isEnaDisAstroExpr = enaDisMatcher(ctx.name.getText()).matches();
            if(isEnaDisAstroExpr && !ctx.bs.isEmpty())
                errMsg = "'%s' enable/disable AstroExpression does not take an expression";
            else if(!isEnaDisAstroExpr && ctx.bs.isEmpty())
                errMsg = "'%s' AstroExpression command without an expression";
        } else if(!ctx.bs.isEmpty()) {
            // not "~" command but has an expression
            errMsg = "'%s' switch command does not take an expression";
            //errMsg = "only '~' switch command, not '%s', takes an expression";
            //errMsg = "'%s' switch command is not a '~hook' command, it does not take an expression";
            // If the expr starts with "~", then maybe "{~" was meant
            Token tok = ctx.bs.get(0).e.start;
            if(tok.getType() == Tilde) {
                errMsg += "; '{~' instead of'%s'?";
                optParam = getText(ctx.b, tok); // ctx.b is '{'
            }
        }
        if(errMsg != null)
            reportError(ctx, errMsg, ParseTreeUtil.getOriginalText(
                    ctx.name, lookup(AstroParseResult.class).getInput()), optParam);
    }
}
private Matcher matcher;
private Matcher enaDisMatcher(String input)
{
    if(matcher == null) {
        Pattern p = Pattern.compile("[=_-]?~0");
        matcher = p.matcher(input);
    } else {
        matcher.reset(input);
    }
    return matcher;
}

// cache the result to avoid giving the same error twice.
private TreeProps<Boolean> macroSwitch_func_callChecked = new TreeProps<>();

/** Check special func args (like for Macro()/Switch()).
 * The return does not indicate if an error occurred.
 * @return true if no more checking needed
 */
private boolean isDoneReportSpecialFuncArgs(Func_callContext ctx)
{
    Boolean  checkComplete = macroSwitch_func_callChecked.get(ctx);
    if(checkComplete != null)
        return checkComplete;

    Function f = Functions.get(ctx.id.getText());
    checkComplete = f.isDoneReportSpecialFuncArgs(ctx);
    macroSwitch_func_callChecked.put(ctx, checkComplete);
    return checkComplete;
}

@Override
public void exitFunc_call(Func_callContext ctx)
{
    isDoneReportSpecialFuncArgs(ctx);
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
