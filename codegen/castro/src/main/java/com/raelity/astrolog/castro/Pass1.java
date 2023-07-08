/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro;

import java.util.ArrayList;

import com.google.common.collect.Range;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.raelity.antlr.ParseTreeUtil;
import com.raelity.astrolog.castro.LineMap.WriteableLineMap;
import com.raelity.astrolog.castro.antlr.AstroParserBaseListener;
import com.raelity.astrolog.castro.antlr.AstroParser;
import com.raelity.astrolog.castro.antlr.AstroParser.Assign_macro_addrContext;
import com.raelity.astrolog.castro.antlr.AstroParser.Assign_switch_addrContext;
import com.raelity.astrolog.castro.antlr.AstroParser.BaseContstraintContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ConstraintContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ExprFuncContext;
import com.raelity.astrolog.castro.antlr.AstroParser.Func_callContext;
import com.raelity.astrolog.castro.antlr.AstroParser.IntegerContext;
import com.raelity.astrolog.castro.antlr.AstroParser.LayoutContext;
import com.raelity.astrolog.castro.antlr.AstroParser.Layout_regionContext;
import com.raelity.astrolog.castro.antlr.AstroParser.LimitContstraintContext;
import com.raelity.astrolog.castro.antlr.AstroParser.MacroContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ProgramContext;
import com.raelity.astrolog.castro.antlr.AstroParser.Rsv_locContext;
import com.raelity.astrolog.castro.antlr.AstroParser.SwitchContext;
import com.raelity.astrolog.castro.antlr.AstroParser.Var1Context;
import com.raelity.astrolog.castro.antlr.AstroParser.VarArrayContext;
import com.raelity.astrolog.castro.antlr.AstroParser.VarArrayInitContext;
import com.raelity.astrolog.castro.antlr.AstroParser.VarContext;
import com.raelity.astrolog.castro.mems.AstroMem;
import com.raelity.astrolog.castro.mems.AstroMem.Var;
import com.raelity.astrolog.castro.mems.Layout;
import com.raelity.astrolog.castro.mems.Macros;
import com.raelity.astrolog.castro.mems.Registers;
import com.raelity.astrolog.castro.mems.Switches;
import com.raelity.astrolog.castro.tables.Functions;

import static com.raelity.astrolog.castro.Util.checkReport;
import static com.raelity.astrolog.castro.Util.lookup;
import static com.raelity.astrolog.castro.Util.replaceLookup;
import static com.raelity.astrolog.castro.Util.reportError;


/** During parse, handle variable declarations, layout, valid function name;
 * and build line index map to interval.
 * Publish {@link AstroMem}s and {@link LineMap} to lookup.
 */
class Pass1 extends AstroParserBaseListener
{
/** use line number as index, first entry is null. */
private final WriteableLineMap wLineMap;
private final Registers registers = new Registers();
private final Macros macros = new Macros();
private final Switches switches = new Switches();
private Layout workingLayout;

static void pass1() {
    AstroParseResult apr = lookup(AstroParseResult.class);
    apr.getParser().addParseListener(new Pass1());
    ProgramContext program = apr.getParser().program();
    apr.setContext(program);
}

public Pass1()
{
    // TODO: check if there's already a linemap
    this.wLineMap = new WriteableLineMap(new ArrayList<>(100));
    replaceLookup(wLineMap.getLineMap());
    replaceLookup(registers);
    replaceLookup(macros);
    replaceLookup(switches);
}

void declareVar(ParserRuleContext _ctx)
{
    TerminalNode idNode;
    IntegerContext addrNode;
    int size;
    switch(_ctx) {
    case Var1Context ctx -> {
        idNode = ctx.Identifier();
        addrNode = ctx.addr;
        size = 1;
    }
    case VarArrayContext ctx -> {
        idNode = ctx.Identifier();
        addrNode = ctx.addr;
        size = Integer.parseInt(ctx.size.getText());
    }
    case VarArrayInitContext ctx -> {
        idNode = ctx.Identifier();
        addrNode = ctx.addr;
        if(ctx.size != null) {
            size = Integer.parseInt(ctx.size.getText());
            if(ctx.init.size() > size)
                reportError(ctx, "too many initializers");
        } else
            size = ctx.init.size();
    }
    case null, default -> throw new IllegalArgumentException();
    }
    if(ParseTreeUtil.hasErrorNode(idNode) ||
            ParseTreeUtil.hasErrorNode(addrNode))
        return;
    Token id = idNode.getSymbol();
    int addr = addrNode == null ? -1 : Integer.parseInt(addrNode.getText());
    Var var = registers.declare(id, size, addr);
    checkReport(var);
}

@Override
public void exitVar(VarContext ctx)
{
    ParseTree child = ctx.getChild(0);
    if(child != null && !(child instanceof ErrorNode))
        declareVar((ParserRuleContext)child);
}

private void declareSwithOrMacro(AstroMem mem, IntegerContext i_ctx, Token id)
{
    int addr;
    if(i_ctx == null || ParseTreeUtil.hasErrorNode(i_ctx))
        addr = -1;
    else
        addr = Integer.parseInt(i_ctx.getText());
    Var var = mem.declare(id, 1, addr);
    checkReport(var);
};

@Override
public void exitMacro(MacroContext ctx)
{
    declareSwithOrMacro(macros, ctx.addr, ctx.id);
}

@Override
public void exitSwitch(SwitchContext ctx)
{
    declareSwithOrMacro(switches, ctx.addr, ctx.id);
}

@Override
public void exitAssign_switch_addr(Assign_switch_addrContext ctx)
{
    declareSwithOrMacro(switches, ctx.addr, ctx.id);
}

@Override
public void exitAssign_macro_addr(Assign_macro_addrContext ctx)
{
    declareSwithOrMacro(macros, ctx.addr, ctx.id);
}

@Override
public void enterLayout(LayoutContext ctx)
{
    if(registers.getVarCount() > 0 || macros.getVarCount() > 0 ||
            switches.getVarCount() > 0)
        // TODO: the following doesn't work to print the line,
        //       see comment in exitEveryRule
        reportError(ctx,
            "layout must be first, before 'var' or 'macro' or 'switch'");
}

@Override
public void exitLayout(LayoutContext ctx)
{
    workingLayout = null;
}

@Override
public void exitLayout_region(Layout_regionContext ctx)
{
    Layout layout = getAstroMem(ctx.start.getType()).getNewLayout();
    if(layout.getMem() == null)
        reportError(ctx, "'%s' layout already specified",
                          ctx.getText());
    workingLayout = layout;
}

AstroMem getAstroMem(int region)
{
    return switch(region) {
    case AstroParser.Memory -> registers;
    case AstroParser.Macro -> macros;
    case AstroParser.Switch -> switches;
    default -> null;
    };
}

private int checkReportSimpleConstraint(ConstraintContext ctx, int curVal)
{
    if(ParseTreeUtil.hasErrorNode(ctx))
        return -1;
    if(curVal >= 0) {
        reportError(ctx, "'%s' already set", ctx.start.getText());
        return -1;
    }
    return Integer.parseInt(ctx.getChild(1).getText());
}

@Override
public void exitBaseContstraint(BaseContstraintContext ctx)
{
    int newVal = checkReportSimpleConstraint(ctx, workingLayout.base);
    if(newVal >= 0)
        workingLayout.base = newVal;
}

@Override
public void exitLimitContstraint(LimitContstraintContext ctx)
{
    int newVal = checkReportSimpleConstraint(ctx, workingLayout.limit);
    if(newVal >= 0)
        workingLayout.limit = newVal;
}

@Override
public void exitRsv_loc(Rsv_locContext ctx)
{
    if(ParseTreeUtil.hasErrorNode(ctx))
        return;
    int r1 = Integer.parseInt(ctx.range.get(0).getText());
    int r2
            = ctx.range.size() == 1 ? r1 + 1
            : Integer.parseInt(ctx.range.get(1).getText());
    workingLayout.reserve.add(Range.closedOpen(r1, r2));
}

@Override
public void exitExprFunc(ExprFuncContext ctx)
{
    Func_callContext fc_ctx = ctx.func_call();
    Integer narg = Functions.narg(fc_ctx.id.getText());
    if(narg == null) {
        reportError(fc_ctx.id,
                    "unknown function '%s'", fc_ctx.id.getText());
        return;
    }
    if(fc_ctx.args.size() != narg)
        reportError(fc_ctx, "function '%s' argument count, expect %d not %d",
                    fc_ctx.id.getText(), narg, fc_ctx.args.size());
}

/** build the LineMap */
@Override
public void exitEveryRule(ParserRuleContext ctx)
{
    // TODO: Would be nice to define the Interval for a line
    //       at enter every rule. Would have to spin
    //       through the line (assuming that works).
    //       Maybe better, build the entire map all at once
    //       before starting to parse.
    //CharStream cs = ctx.start.getInputStream();
    //String t = cs.toString();

    if(ctx.start == null || ctx.stop == null)
        return;
    int startIndex = ctx.start.getStartIndex();
    if(startIndex < 0) {
        return;
    }
    int line = ctx.start.getLine();
    startIndex -= ctx.start.getCharPositionInLine();
    wLineMap.includeLineStart(line, startIndex);
    int stopIndex = ctx.stop.getStopIndex();
    wLineMap.includeLineStop(line, stopIndex);
}
    
}
