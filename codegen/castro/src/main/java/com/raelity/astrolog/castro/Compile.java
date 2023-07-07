/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro;

import java.io.PrintWriter;
import java.util.EnumSet;

import com.google.common.collect.RangeSet;

import com.raelity.astrolog.castro.Castro.CastroErr;
import com.raelity.astrolog.castro.Castro.CastroOut;
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
 * Pass1 - Layout info, var/mem symbol tables, check func name/nargs,
 *         Build LineMap.
 * Pass2 - Check used variables are defined.
 *         Check no blanks in switch cmd, proper switch expression usage.
 * Pass3 - Generate code.
 * Pass4 - Output code. TODO: integrate into pass3?
 * @author err
 */
public class Compile
{
private Compile() { }

static void compile()
{
    AstroParseResult apr = lookup(AstroParseResult.class);
    PrintWriter out = lookup(CastroOut.class).pw();
    PrintWriter err = lookup(CastroErr.class).pw();

    Pass1.pass1();

    int currentErrorCount = 0;
    if(apr.hasError()) {
        err.printf("Pass1: %d syntax errors\n", apr.getParser().getNumberOfSyntaxErrors());
        err.printf("Pass1: %d other errors\n", apr.errors() - currentErrorCount);
        currentErrorCount = apr.errors();
    }

    Registers registers = lookup(Registers.class);
    Macros macros = lookup(Macros.class);
    Switches switches = lookup(Switches.class);


    registers.dumpLayout(out);
    macros.dumpLayout(out);
    switches.dumpLayout(out);

    applyLayoutsAndAllocate();

    if(apr.errors() > currentErrorCount) {
        err.printf("Allocation: %d errors\n", apr.errors() - currentErrorCount);
        currentErrorCount = apr.errors();
    }

    //compile.registers.dumpAllocation(out, EnumSet.of(BUILTIN));
    registers.dumpVars(out, true, EnumSet.of(BUILTIN));
    macros.dumpVars(out, true, EnumSet.of(BUILTIN));
    switches.dumpVars(out, true, EnumSet.of(BUILTIN));

    Pass2.pass2();
    if(apr.errors() > currentErrorCount) {
        err.printf("Pass2: %d errors\n", apr.errors() - currentErrorCount);
        currentErrorCount = apr.errors();
    }
    if(apr.hasError())
        return;

    Pass3.pass3();
    if(apr.errors() > currentErrorCount) {
        err.printf("Code generation: %d errors\n", apr.errors() - currentErrorCount);
        return;
    }

    PassOutput.passOutput();
    if(apr.errors() > currentErrorCount) {
        err.printf("Code output: %d errors\n", apr.errors() - currentErrorCount);
        return;
    }

}

private static void applyLayoutsAndAllocate()
{
    for(AstroMem mem : lookupAll(AstroMem.class)) {
        if(mem == null)
            continue;
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
