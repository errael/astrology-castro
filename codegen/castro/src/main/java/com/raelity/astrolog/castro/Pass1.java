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
import com.raelity.astrolog.castro.antlr.AstroBaseListener;
import com.raelity.astrolog.castro.antlr.AstroParser;
import com.raelity.astrolog.castro.antlr.AstroParser.ConstraintContext;
import com.raelity.astrolog.castro.antlr.AstroParser.IntegerContext;
import com.raelity.astrolog.castro.antlr.AstroParser.LayoutContext;
import com.raelity.astrolog.castro.antlr.AstroParser.Layout_regionContext;
import com.raelity.astrolog.castro.antlr.AstroParser.MacroContext;
import com.raelity.astrolog.castro.antlr.AstroParser.Rsv_locContext;
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


/** During parse, handle variable declarations and layout
 * and build line index map to interval.
 * Publish {@link AstroMem}s and {@link LineMap} to lookup.
 */
class Pass1 extends AstroBaseListener
{
    /** use line number as index, first entry is null. */
    private final WriteableLineMap wLineMap;
    Registers registers = new Registers();
    Macros macros = new Macros();
    Switches switches = new Switches();
    Layout workingLayout;

    public Pass1()
    {
        this.wLineMap = new WriteableLineMap(new ArrayList<>(100));
        Util.addLookup(wLineMap.getLineMap());
        Util.addLookup(registers);
        Util.addLookup(macros);
        Util.addLookup(switches);
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
                    Util.reportError(ctx, "too many initializers");
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
        Util.checkReport(var);
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
        if(ctx.addr == null || ParseTreeUtil.hasErrorNode(ctx.addr))
            addr = -1;
        else
            addr = Integer.parseInt(ctx.addr.getText());
        Var var = macros.declare(ctx.Identifier().getSymbol(), 1, addr);
        Util.checkReport(var);
    }

    @Override
    public void enterLayout(LayoutContext ctx)
    {
        if(registers.getVarCount() > 0 || macros.getVarCount() > 0 ||
                switches.getVarCount() > 0)
            // TODO: the following doesn't work to print the line,
            //       see comment in exitEveryRule
            Util.reportError(ctx,
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
            Util.reportError(ctx, "'%s' layout already specified",
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

    @Override
    public void exitConstraint(ConstraintContext ctx)
    {
        if(ctx.start.getType() == AstroParser.Reserve ||
                ParseTreeUtil.hasErrorNode(ctx))
            return;
        int curVal = switch(ctx.start.getType()) {
        case AstroParser.Base -> workingLayout.base;
        case AstroParser.Limit -> workingLayout.limit;
        default -> -1;
        };
        if(curVal >= 0) {
            Util.reportError(ctx, "'%s' already set", ctx.start.getText());
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
        if(ParseTreeUtil.hasErrorNode(ctx))
            return;
        int r1 = Integer.parseInt(ctx.range.get(0).getText());
        int r2
                = ctx.range.size() == 1 ? r1 + 1
                : Integer.parseInt(ctx.range.get(1).getText());
        workingLayout.reserve.add(Range.closedOpen(r1, r2));
    }

    /** build the LineMap */
    @Override
    public void exitEveryRule(ParserRuleContext ctx)
    {
        // TODO: Would be nice to define the Interval for a line
        //       at enter every rule. Would have to spin
        //       through the line (assuming that works).
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
