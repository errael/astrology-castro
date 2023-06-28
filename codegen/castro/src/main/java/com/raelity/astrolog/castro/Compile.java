/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro;

import java.io.PrintWriter;
import java.util.EnumSet;
import java.util.List;

import com.google.common.collect.RangeSet;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.xpath.XPath;

import com.raelity.astrolog.castro.Castro.CastroErr;
import com.raelity.astrolog.castro.Castro.CastroOut;
import com.raelity.astrolog.castro.antlr.AstroParser.AstroExprStatementContext;
import com.raelity.astrolog.castro.antlr.AstroParser.LvalContext;
import com.raelity.astrolog.castro.antlr.AstroParser.MacroContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ProgramContext;
import com.raelity.astrolog.castro.mems.AstroMem;
import com.raelity.astrolog.castro.mems.AstroMem.Var;
import com.raelity.astrolog.castro.mems.Macros;
import com.raelity.astrolog.castro.mems.Registers;
import com.raelity.astrolog.castro.mems.Switches;

import static com.raelity.astrolog.castro.Util.lookup;
import static com.raelity.astrolog.castro.Util.lookupAll;
import static com.raelity.astrolog.castro.mems.AstroMem.Var.VarState.*;

/**
 * Run the passes. <br>
 * Pass1 - layout info, var/mem symbol tables, check func name/nargs.
 * Pass2 - check used variables are defined.
 * Pass3 - generate code.
 * @author err
 */
public class Compile
{
private Compile() { }

static void compile()
{
    AstroParseResult apr = lookup(AstroParseResult.class);

    apr.getParser().addParseListener(new Pass1());
    ProgramContext program = apr.getParser().program();
    apr.setContext(program);


    PrintWriter out = lookup(CastroOut.class).pw;
    PrintWriter err = lookup(CastroErr.class).pw;

    if(apr.hasError()) {
        err.printf("Pass1: %d syntax errors\n", apr.getParser().getNumberOfSyntaxErrors());
        err.printf("Pass1: %d other errors\n", apr.errors());
    }

    lookup(Registers.class).dumpLayout(out);
    lookup(Macros.class).dumpLayout(out);
    lookup(Switches.class).dumpLayout(out);

    applyLayoutsAndAllocate();

    if(apr.hasError())
        err.printf("After Allocation: %d errors\n", apr.errors());

    //compile.registers.dumpAllocation(out, EnumSet.of(BUILTIN));
    lookup(Registers.class).dumpVars(out, true, EnumSet.of(BUILTIN));
    lookup(Macros.class).dumpVars(out, true, EnumSet.of(BUILTIN));
    lookup(Switches.class).dumpVars(out, true, EnumSet.of(BUILTIN));

    int pass1ErrorCount = apr.errors();

    Pass2.pass2();

    int pass2ErrorCount = apr.errors() - pass1ErrorCount;
    if(pass2ErrorCount != 0)
        err.printf("Pass2: %d errors\n", pass2ErrorCount);
    if(apr.hasError())
        return;
    out.printf("PROCEEDING TO CODE GENERATION\n");
    Pass3.pass3();

    StringBuilder sb = new StringBuilder(100);
    for(ParseTree tree : XPath.findAll(apr.getProgram(), "//macro", apr.getParser())) {
        MacroContext ctx = (MacroContext)tree;
        List<AstroExprStatementContext> es = ctx.s;

        sb.setLength(0);
        sb.append("// MACRO ").append(ctx.Identifier().getText());
        if(ctx.addr != null) {
            sb.append("@").append(apr.prefixExpr.removeFrom(ctx.addr));
        }
        sb.append('(').append(es.size()).append(')');

        out.printf("\n%s\n", sb.toString());
        for(AstroExprStatementContext s : es) {
            out.printf("    %s\n", apr.prefixExpr.removeFrom(s.astroExpr.expr));
        }
    }

}

static void applyLayoutsAndAllocate()
{
    for(AstroMem mem : lookupAll(AstroMem.class)) {
        if(mem == null)
            return;
        // warn if ASSIGN in reserve area
        RangeSet<Integer> reserve = mem.getLayoutReserve();
        for(var e : mem.getAllocationMap().entrySet()) {
            Var var = e.getValue();
            if(var.hasState(ASSIGN) && !var.hasState(LIMIT)
                    && reserve.intersects(e.getKey()))
                Util.report(false, var.getId(),
                       "'%s' assigned to reserve area", var.getName());
        }

        mem.allocate();
    }
}
    
}
