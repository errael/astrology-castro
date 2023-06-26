/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro;

import java.io.PrintWriter;
import java.util.EnumSet;

import com.google.common.collect.RangeSet;

import com.raelity.astrolog.castro.Castro.CastroErr;
import com.raelity.astrolog.castro.Castro.CastroOut;
import com.raelity.astrolog.castro.antlr.AstroParser.ProgramContext;
import com.raelity.astrolog.castro.lib.CentralLookup;
import com.raelity.astrolog.castro.mems.AstroMem;
import com.raelity.astrolog.castro.mems.AstroMem.Var;
import com.raelity.astrolog.castro.mems.Macros;
import com.raelity.astrolog.castro.mems.Registers;
import com.raelity.astrolog.castro.mems.Switches;

import static com.raelity.astrolog.castro.Util.lookup;
import static com.raelity.astrolog.castro.mems.AstroMem.Var.VarState.*;

/**
 *
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

    applyLayoutsAndAllocate();

    if(apr.hasError())
        err.printf("After Allocation: %d errors\n", apr.errors());

    //compile.registers.dumpAllocation(out, EnumSet.of(BUILTIN));
    lookup(Registers.class).dumpVars(out, true, EnumSet.of(BUILTIN));
    lookup(Macros.class).dumpVars(out, true, EnumSet.of(BUILTIN));
    lookup(Switches.class).dumpVars(out, true, EnumSet.of(BUILTIN));

    //ParseTreeWalker walker = new ParseTreeWalker();
    //walker.walk(compile.new Pass1(), program);
}

static void applyLayoutsAndAllocate()
{
    for(AstroMem mem : CentralLookup.getDefault().lookupAll(AstroMem.class)) {
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
