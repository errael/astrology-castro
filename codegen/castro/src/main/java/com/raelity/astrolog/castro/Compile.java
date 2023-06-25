/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;

import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.raelity.astrolog.castro.Castro.CastroErr;
import com.raelity.astrolog.castro.Castro.CastroOut;
import com.raelity.astrolog.castro.LineMap.WriteableLineMap;
import com.raelity.astrolog.castro.antlr.AstroBaseListener;
import com.raelity.astrolog.castro.antlr.AstroParser;
import com.raelity.astrolog.castro.antlr.AstroParser.ConstraintContext;
import com.raelity.astrolog.castro.antlr.AstroParser.IntegerContext;
import com.raelity.astrolog.castro.antlr.AstroParser.LayoutContext;
import com.raelity.astrolog.castro.antlr.AstroParser.Layout_regionContext;
import com.raelity.astrolog.castro.antlr.AstroParser.MacroContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ProgramContext;
import com.raelity.astrolog.castro.antlr.AstroParser.Rsv_locContext;
import com.raelity.astrolog.castro.antlr.AstroParser.Var1Context;
import com.raelity.astrolog.castro.antlr.AstroParser.VarArrayContext;
import com.raelity.astrolog.castro.antlr.AstroParser.VarArrayInitContext;
import com.raelity.astrolog.castro.antlr.AstroParser.VarContext;
import com.raelity.astrolog.castro.mems.AstroMem;
import com.raelity.astrolog.castro.mems.AstroMem.Var;
import com.raelity.astrolog.castro.mems.Macros;
import com.raelity.astrolog.castro.mems.Registers;
import com.raelity.astrolog.castro.mems.Switches;

import static com.raelity.astrolog.castro.Util.addLookup;
import static com.raelity.astrolog.castro.Util.checkReport;
import static com.raelity.astrolog.castro.Util.lookup;
import static com.raelity.astrolog.castro.Util.tokenLoc;
import static com.raelity.astrolog.castro.mems.AstroMem.Var.VarState.*;
import static com.raelity.antlr.ParseTreeUtil.hasErrorNode;

/**
 *
 * @author err
 */
public class Compile
{
static void compile(AstroParseResult apr)
{

    Compile compile = new Compile(apr);

    apr.getParser().addParseListener(compile.new Pass1());

    ProgramContext program = apr.getParser().program();
    apr.setContext(program);


    PrintWriter out = lookup(CastroOut.class).pw;
    PrintWriter err = lookup(CastroErr.class).pw;

    if(apr.hasError()) {
        err.printf("Pass1: %d syntax errors\n", apr.getParser().getNumberOfSyntaxErrors());
        err.printf("Pass1: %d other errors\n", apr.errors());
    }

    compile.applyLayoutsAndAllocate();

    if(apr.hasError())
        err.printf("After Allocation: %d errors\n", apr.errors());

    if(Boolean.FALSE) {
        out.printf("registerLayout: %s\n", compile.registerLayout);
        out.printf("macroLayout: %s\n", compile.macroLayout);
        out.printf("switchLayout: %s\n", compile.switchLayout);
    }

    //compile.registers.dumpAllocation(out, EnumSet.of(BUILTIN));
    compile.registers.dumpVars(out, true, EnumSet.of(BUILTIN));
    compile.macros.dumpVars(out, true, EnumSet.of(BUILTIN));
    compile.switches.dumpVars(out, true, EnumSet.of(BUILTIN));

    //ParseTreeWalker walker = new ParseTreeWalker();
    //walker.walk(compile.new Pass1(), program);
}

Registers registers = new Registers();
Switches switches = new Switches();
Macros macros = new Macros();
/** use line number as index, first entry is null. */
private final WriteableLineMap wLineMap;
//private final List<Interval> wLineMap;
private final AstroParseResult apr;

Layout registerLayout;
Layout macroLayout;
Layout switchLayout;
Layout workingLayout;

private PrintWriter getErr()
{
    return lookup(CastroErr.class).pw;
}


public Compile(AstroParseResult apr)
{
    this.wLineMap = new WriteableLineMap(new ArrayList<>(100));
    this.apr = apr;
    addLookup(wLineMap.getLineMap());
}

void applyLayoutsAndAllocate()
{
    for(Layout layout : new Layout[] {registerLayout, macroLayout, switchLayout}) {
        if(layout == null)
            return;
        if(layout.base >= 0)
            layout.mem.lowerLimit(layout.base - 1);
        if(layout.limit >= 0)
            layout.mem.upperLimit(layout.limit);
        // warn if ASSIGN in reserve area
        for(var e : layout.mem.getAllocationMap().entrySet()) {
            Var var = e.getValue();
            if(var.hasState(ASSIGN) && !var.hasState(LIMIT)
                    && layout.reserve.intersects(e.getKey()))
                report(false, var.getId(),
                       "'%s' assigned to reserve area", var.getName());
        }
        layout.mem.rangeLimit(layout.reserve);

        layout.mem.allocate();
    }
}

void reportError(ParserRuleContext ctx, Object... msg)
{
    reportError(ctx.start, msg);
}

void reportError(Token token, Object... msg)
{
    report(true, token, msg);
}

void report(boolean fError, Token token, Object... msg)
{
    String optMsg = msg.length == 0 ? "" : String.format(
            (String)msg[0], Arrays.copyOfRange(msg, 1, msg.length));
    getErr().printf("%s '%s' %s %s\n", tokenLoc(token), Util.getLineText(token),
                    (fError ? "Error:" : "Warn:"), optMsg);
    if(fError)
        lookup(AstroParseResult.class).countError();
}

    /** During parse, handle variable declarations and layout
     * and build line index map to interval.
     */
    class Pass1 extends AstroBaseListener
    {

    void declareVar(ParserRuleContext _ctx)
    {
        TerminalNode idNode;
        IntegerContext addrNode;
        int size;
        switch(_ctx)
        {

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
        if(hasErrorNode(idNode) || hasErrorNode(addrNode))
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

    @Override
    public void exitMacro(MacroContext ctx)
    {
        int addr;
        if(ctx.addr == null || hasErrorNode(ctx.addr))
            addr = -1;
        else
            addr = Integer.parseInt(ctx.addr.getText());
        Var var = macros.declare(ctx.Identifier().getSymbol(), 1, addr);
        checkReport(var);
    }

    @Override
    public void enterLayout(LayoutContext ctx)
    {
        if(registers.getVarCount() > 0 || macros.getVarCount() > 0
                || switches.getVarCount() > 0)
            reportError(ctx, "layout must be first, before 'var' or 'macro'");
    }

    @Override
    public void exitLayout(LayoutContext ctx)
    {
        int region = ctx.layout_region().start.getType();
        if(getRegion(region) == null)
            switch (region) {
            case AstroParser.Memory -> registerLayout = workingLayout;
            case AstroParser.Macro -> macroLayout = workingLayout;
            case AstroParser.Switch -> switchLayout = workingLayout;
            }
    }

    Layout getRegion(int region)
    {
        return switch(region) {
        case AstroParser.Memory -> registerLayout;
        case AstroParser.Macro -> macroLayout;
        case AstroParser.Switch -> switchLayout;
        default -> null;
        };
    }
    
    int getTNType(ParserRuleContext ctx, int i)
    {
        return ((TerminalNode)ctx.getChild(i)).getSymbol().getType();
    }

    @Override
    public void exitLayout_region(Layout_regionContext ctx)
    {
        Layout region = getRegion(ctx.start.getType());
        if(region != null)
            reportError(ctx, "'%s' layout already specified", ctx.getText());
        workingLayout = new Layout(ctx.start.getType());
    }
    
    @Override
    public void exitConstraint(ConstraintContext ctx)
    {
        if(ctx.start.getType() == AstroParser.Reserve
                || hasErrorNode(ctx))
            return;
        int curVal = switch(ctx.start.getType()) {
        case AstroParser.Base -> workingLayout.base;
        case AstroParser.Limit -> workingLayout.limit;
        default -> -1;
        };
        if(curVal >= 0) {
            reportError(ctx, "'%s' already set", ctx.start.getText());
            return;
        }
        int val = Integer.parseInt(ctx.integer().getText());
        switch(ctx.start.getType()) {
        case AstroParser.Base -> workingLayout.base = val;
        case AstroParser.Limit -> workingLayout.limit = val;
        }
    }
    
    @Override
    public void exitRsv_loc(Rsv_locContext ctx)
    {
        if(hasErrorNode(ctx))
            return;
        int r1 = Integer.parseInt(ctx.range.get(0).getText());
        int r2 = ctx.range.size() == 1 ? r1 + 1
                 : Integer.parseInt(ctx.range.get(1).getText());
        workingLayout.reserve.add(Range.closedOpen(r1, r2));
    }
    

    /** build the LineMap */
    @Override
    public void exitEveryRule(ParserRuleContext ctx)
    {
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

    class Layout
    {
    AstroMem mem;
    int base;
    //int stack;
    int limit;
    final RangeSet<Integer> reserve = TreeRangeSet.create();
    
    public Layout(int region)
    {
        base = -1;
        limit = -1;
        // it is convenient to have ref to the associated region
        mem = switch(region) {
        case AstroParser.Memory -> registers;
        case AstroParser.Macro -> macros;
        case AstroParser.Switch -> switches;
        default -> null;
        };
    }

        @Override
        public String toString()
        {
            return "Layout{" + "base=" + base + ", limit=" + limit + ", reserve=" +
                    reserve + '}';
        }
    
    }
    
}
