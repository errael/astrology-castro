/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;

import com.google.common.collect.RangeSet;

import com.raelity.astrolog.castro.Castro.CastroErr;
import com.raelity.astrolog.castro.Castro.CastroOut;
import com.raelity.astrolog.castro.Castro.MacrosAccum;
import com.raelity.astrolog.castro.Castro.RegistersAccum;
import com.raelity.astrolog.castro.Castro.SwitchesAccum;
import com.raelity.astrolog.castro.mems.AstroMem;
import com.raelity.astrolog.castro.mems.AstroMem.Var;
import com.raelity.astrolog.castro.mems.Macros;
import com.raelity.astrolog.castro.mems.Registers;
import com.raelity.astrolog.castro.mems.Switches;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import static com.raelity.astrolog.castro.Util.addLookup;
import static com.raelity.astrolog.castro.Util.lookup;
import static com.raelity.astrolog.castro.Util.lookupAll;
import static com.raelity.astrolog.castro.Util.removeLookup;
import static com.raelity.astrolog.castro.Util.replaceLookup;
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
private static CastroOut out;
private Compile() { }

/** @return false if there is an error */
static boolean compile(List<String> inputFiles, String outName)
{
    // the Accum have info for all the files
    addLookup(new RegistersAccum(new Registers(), new Registers()));
    addLookup(new MacrosAccum(new Macros(), new Macros()));
    addLookup(new SwitchesAccum(new Switches(), new Switches()));

    // First parse the input files, setup file parse IO
    // and record declarations.

    boolean someError = false;
    for(String inputFile : inputFiles) {

        CastroIO castroIO = new CastroIO(inputFile, outName, false);
        if(castroIO.getErrorMsg() != null)
            Castro.usage(castroIO.getErrorMsg());
        if(castroIO.isAbort()) {
            someError = true;
            continue;
        }
        
        out = new CastroOut(castroIO.getOutputWriter(), castroIO.getBaseName(),
                castroIO.getOutDir(), inputFile);
        replaceLookup(out);
        
        CastroIO.outputFileHeader(out.pw(), ";");
        
        AstroParseResult apr = castroIO.getApr();
        replaceLookup(apr);

        Compile.compileOneFile();

        if(out != null) {
            out.pw().close();
            removeLookup(out);
        }
        
        if(apr.hasError()) {
            someError = true;
            continue;
        }
        
        castroIO.markSuccess();
    }
    
    return !someError;
}

static void compileOneFile()
{
    // Setup things associated with input file for the duration.
    Registers registers = new Registers();
    Macros macros = new Macros();
    Switches switches = new Switches();

    replaceLookup(registers);
    replaceLookup(macros);
    replaceLookup(switches);

    AstroParseResult apr = lookup(AstroParseResult.class);
    PrintWriter err = lookup(CastroErr.class).pw();



    Pass1.pass1();

    int currentErrorCount = 0;
    if(apr.hasError()) {
        err.printf("Pass1: %d syntax errors\n", apr.getParser().getNumberOfSyntaxErrors());
        err.printf("Pass1: %d other errors\n", apr.errors() - currentErrorCount);
        currentErrorCount = apr.errors();
    }

    applyLayoutsAndAllocate();

    createDef();

    if(apr.errors() > currentErrorCount) {
        err.printf("Allocation: %d errors\n", apr.errors() - currentErrorCount);
        currentErrorCount = apr.errors();
    }

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
    if(apr.errors() > currentErrorCount)
        err.printf("Code output: %d errors\n", apr.errors() - currentErrorCount);

    // Copy this pass' variables to defined
    for(AstroMem mem : lookupAll(AstroMem.class)) {
        mem.updateDefined();
    }
}

/** Create and output the .def file containing allocations/definitions.
 * It con
 */
private static void createDef()
{
    CastroOut castroOut = lookup(CastroOut.class);
    if(castroOut.outDir() == null)
        return;
    Path defPath = castroOut.outDir().resolve(castroOut.baseName() + Castro.DEF_EXT);
    try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(
            defPath, WRITE, TRUNCATE_EXISTING, CREATE))) {
        CastroIO.outputFileHeader(out, "//");

        Registers registers = lookup(Registers.class);
        Macros macros = lookup(Macros.class);
        Switches switches = lookup(Switches.class);
        out.println();

        // TODO: output options
        registers.dumpLayout(out);
        macros.dumpLayout(out);
        switches.dumpLayout(out);
        out.println();

        // TODO: output options
        //compile.registers.dumpAllocation(out, EnumSet.of(BUILTIN));
        registers.dumpVars(out, true, EnumSet.of(BUILTIN, EXTERN));
        out.println();
        macros.dumpVars(out, true, EnumSet.of(BUILTIN, EXTERN));
        out.println();
        switches.dumpVars(out, true, EnumSet.of(BUILTIN, EXTERN));
        out.println();
    } catch(IOException ex) {
        lookup(AstroParseResult.class).countError();
        lookup(CastroErr.class).pw().printf("Error: '%s' Problem writing %s\n",
                                            ex.getClass().getSimpleName(), defPath);
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
