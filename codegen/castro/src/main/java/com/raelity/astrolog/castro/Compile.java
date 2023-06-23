/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.raelity.astrolog.castro.Castro.CastroErr;
import com.raelity.astrolog.castro.Castro.CastroOut;
import com.raelity.astrolog.castro.LineMap.WriteableLineMap;
import com.raelity.astrolog.castro.antlr.AstroBaseListener;
import com.raelity.astrolog.castro.antlr.AstroParser.IntegerContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ProgramContext;
import com.raelity.astrolog.castro.antlr.AstroParser.Var1Context;
import com.raelity.astrolog.castro.antlr.AstroParser.VarArrayContext;
import com.raelity.astrolog.castro.antlr.AstroParser.VarArrayInitContext;
import com.raelity.astrolog.castro.antlr.AstroParser.VarContext;
import com.raelity.astrolog.castro.mems.AstroMem.Var;
import com.raelity.astrolog.castro.mems.Macros;
import com.raelity.astrolog.castro.mems.Registers;
import com.raelity.astrolog.castro.mems.Switches;

import static com.raelity.astrolog.castro.Util.addLookup;
import static com.raelity.astrolog.castro.Util.checkReport;
import static com.raelity.astrolog.castro.Util.lookup;
import static com.raelity.astrolog.castro.Util.tokenLoc;
import static com.raelity.astrolog.castro.mems.AstroMem.Var.VarState.*;

/**
 *
 * @author err
 */
public class Compile
{

Registers registers = new Registers();
Switches switches = new Switches();
Macros macros = new Macros();
/** use line number as index, first entry is null. */
private final WriteableLineMap wLineMap;
//private final List<Interval> wLineMap;
private final AstroParseResult apr;

private PrintWriter getErr()
{
    return lookup(CastroErr.class).pw;
}

static void compile(AstroParseResult apr)
{
    Compile compile = new Compile(apr);

    apr.getParser().addParseListener(compile.new ParsePass0());
    ProgramContext program = apr.getParser().program();
    apr.setContext(program);

    PrintWriter out = lookup(CastroOut.class).pw;
    PrintWriter err = lookup(CastroErr.class).pw;

    if(apr.errors() != 0)
        err.printf("After ParsePass0: %d errors\n", apr.errors());

    // TODO: apply memory constraints before allocate
    compile.registers.lowerLimit(48);

    compile.registers.allocate();

    if(apr.errors() != 0) {
        err.printf("After Allocation: %d errors\n", apr.errors());
        return;
    }

    compile.registers.dumpAllocation(out, EnumSet.of(BUILTIN));
    compile.registers.dumpVars(out, true, EnumSet.of(BUILTIN));
}

public Compile(AstroParseResult apr)
{
    this.wLineMap = new WriteableLineMap(new ArrayList<>(100));
    this.apr = apr;
    addLookup(wLineMap.getLineMap());
}

void reportError(ParserRuleContext ctx, Object... msg)
{
    String optMsg = msg.length == 0 ? "" : String.format(
            (String)msg[0], Arrays.copyOfRange(msg, 1, msg.length));
    getErr().printf("%s '%s' %s\n", tokenLoc(ctx.start),
                    Util.getLineText(ctx.start), optMsg);
    lookup(AstroParseResult.class).countError();
}

    /** During parse, handle variable declarations
     * and build line index to interval.
     */
    class ParsePass0 extends AstroBaseListener
    {
    void declareVar(ParserRuleContext ctx00)
    {
        TerminalNode idNode;
        IntegerContext addrNode;
        int size;
        switch(ctx00)
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
        Token id = idNode.getSymbol();
        int addr = addrNode == null ? -1 : Integer.parseInt(addrNode.getText());
        Var var = registers.declare(id, size, addr);
        checkReport(var);
    }
    
    @Override
    public void exitVar(VarContext ctx)
    {
        ParseTree child = ctx.getChild(0);
        declareVar((ParserRuleContext)child);
    }

    @Override
    public void exitEveryRule(ParserRuleContext ctx)
    {
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

}
