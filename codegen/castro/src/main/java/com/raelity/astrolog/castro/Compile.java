/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.RangeSet;

import com.raelity.astrolog.castro.Castro.CastroErr;
import com.raelity.astrolog.castro.Castro.MacrosAccum;
import com.raelity.astrolog.castro.Castro.RegistersAccum;
import com.raelity.astrolog.castro.Castro.SwitchesAccum;
import com.raelity.astrolog.castro.mems.AstroMem;
import com.raelity.astrolog.castro.mems.AstroMem.Var;
import com.raelity.astrolog.castro.mems.AstroMem.Var.VarState;
import com.raelity.astrolog.castro.mems.Macros;
import com.raelity.astrolog.castro.mems.Registers;
import com.raelity.astrolog.castro.mems.Switches;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import static com.raelity.astrolog.castro.Util.addLookup;
import static com.raelity.astrolog.castro.Util.lookup;
import static com.raelity.astrolog.castro.Util.lookupAll;
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
private Compile() { }

private record FileData(CastroIO castroIO,
        Registers registers, Macros macros, Switches switches){};
private static Map<String,FileData> fileData = new HashMap<>();
private static boolean memoryLocked;

/** @return false if there is an error */
static boolean compile(List<String> inputFiles, String outName)
{
    // the Accum have info for all the files
    addLookup(new RegistersAccum(new Registers(), new Registers(), new Registers(), new Registers()));
    addLookup(new MacrosAccum(new Macros(), new Macros(), new Macros(), new Macros()));
    addLookup(new SwitchesAccum(new Switches(), new Switches(), new Switches(), new Switches()));

    boolean someError = false;

    //////////////////////////////////////////////////////////////////////
    //
    // First parse the input files, setup file parse IO
    // and record declarations.
    //

    for(String inputFile : inputFiles) {

        CastroIO castroIO = new CastroIO(inputFile, outName, false);
        if(castroIO.getErrorMsg() != null)
            Castro.usage(castroIO.getErrorMsg());
        if(castroIO.isAbort()) {
            someError = true;
            continue;
        }

        // Setup things associated with input file for the duration.

        // need castroIO for mem space creation
        replaceLookup(castroIO);
        FileData data = new FileData(castroIO, new Registers(),
                                     new Macros(), new Switches());
        fileData.put(inputFile, data);
        AstroParseResult apr = initLookup(data);
        
        CastroIO.outputFileHeader(castroIO.pw(), ";");

        parseOneFile();
        
        if(apr.hasError())
            someError = true;
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Do the allocations for each file, supplying the defined
    // from all files except the one being allocated.
    //

    //debugDumpAllvars(inputFiles, false);
    for(String inputFile : inputFiles) {
        FileData data = fileData.get(inputFile);
        if(data == null)
            continue;
        initLookup(data);

        applyLayoutsAndAllocate();
    }

    // Collect all the variables into "global" containers
    for(String inputFile : inputFiles) {
        FileData data = fileData.get(inputFile);
        if(data == null)
            continue;
        initLookup(data);

        for(AstroMem mem : lookupAll(AstroMem.class)) {
            mem.addToGlobal();
        }
    }

    Registers globalRegisters = lookup(RegistersAccum.class).global();
    Macros globalMacros = lookup(MacrosAccum.class).global();
    Switches globalSwitches = lookup(SwitchesAccum.class).global();

    globalRegisters.lockMemory();
    globalMacros.lockMemory();
    globalSwitches.lockMemory();
    memoryLocked = true;

    for(AstroMem mem : lookupAll(AstroMem.class)) {
        AstroMem memGlobal = mem.getAccum().global();
        if(memGlobal.getErrorVarsCount() > 0) {
            lookup(CastroErr.class).pw().printf("Internal Error: globals allocation problem\n");
            someError = true;
        }
        if(memGlobal.getErrorVarsCount() > 0) {
            lookup(CastroErr.class).pw().printf("\nGLOBAL check %s, errors %d\n",
                                            mem.memSpaceName,
                                            memGlobal.getErrorVarsCount());
            // TODO: put these output into above error case
            memGlobal.dumpVars(lookup(CastroErr.class).pw(), true);
            lookup(CastroErr.class).pw().println();
            memGlobal.dumpErrors(lookup(CastroErr.class).pw());
        }
    }

    // These remain for the duration
    replaceLookup(globalRegisters);
    replaceLookup(globalMacros);
    replaceLookup(globalSwitches);


    //////////////////////////////////////////////////////////////////////
    //
    // Now that the variables are allocated, proceed with compilation
    //

    for(String inputFile : inputFiles) {
        FileData data = fileData.get(inputFile);
        if(data == null)
            continue;
        AstroParseResult apr = initLookup(data);

        Compile.compileOneFile();

        data.castroIO.pw().close();
        
        if(apr.hasError()) {
            someError = true;
            continue;
        }
        
        data.castroIO.markSuccess();
    }

    //FileData data = fileData.get(inputFiles.get(inputFiles.size()-1));
    //data.registers.dumpVars(lookup(CastroErr.class).pw(), true);
    if(Boolean.FALSE) {
        lookup(CastroErr.class).pw().println("\nDUMP defined");
        lookup(RegistersAccum.class).defined()
                .dumpVars(lookup(CastroErr.class).pw(), true);
        lookup(CastroErr.class).pw().println("\nDUMP alloc");
        lookup(RegistersAccum.class).alloc()
                .dumpVars(lookup(CastroErr.class).pw(), true);
    }

    //debugDumpAllvars(inputFiles, true);

    return !someError;
}

/** Run the parser, collect declarations... */
private static void parseOneFile()
{
    AstroParseResult apr = lookup(AstroParseResult.class);
    PrintWriter err = lookup(CastroErr.class).pw();

    Pass1.pass1();

    if(apr.hasError()) {
        err.printf("Pass1: %d syntax errors\n", apr.getParser().getNumberOfSyntaxErrors());
        err.printf("Pass1: %d other errors\n", apr.errors());
    }

    // Copy this pass' variables to defined accumulation of variables;
    // some may not have addresses.
    // The next file that is parsed will check it's
    // names and addresses against the accumulated defines.
    for(AstroMem mem : lookupAll(AstroMem.class)) {
        mem.addToDefined();
    }
}

private static void compileOneFile()
{

    AstroParseResult apr = lookup(AstroParseResult.class);
    PrintWriter err = lookup(CastroErr.class).pw();

    int currentErrorCount = 0;
    currentErrorCount = apr.errors();

    //applyLayoutsAndAllocate();

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
}

private static AstroParseResult initLookup(FileData data)
{
    if(!memoryLocked) {
        replaceLookup(data.registers);
        replaceLookup(data.macros);
        replaceLookup(data.switches);
    }

    CastroIO castroIO = data.castroIO;
    replaceLookup(castroIO);
    
    AstroParseResult apr = castroIO.getApr();
    replaceLookup(apr);
    return apr;
    
}

private static void debugDumpAllvars(List<String> inputFiles, boolean includeDefined)
{
    for(String inputFile : inputFiles) {
        FileData data = fileData.get(inputFile);
        if(data == null)
            continue;
        AstroParseResult apr = initLookup(data);

        PrintWriter err = lookup(CastroErr.class).pw();
        err.println();
        EnumSet<VarState> skip = includeDefined
                                 ? EnumSet.of(BUILTIN)
                                 : EnumSet.of(BUILTIN, DEFINED);
        data.registers.dumpVars(err, true, skip);
    }
}

/** Create and output the .def file containing allocations/definitions.
 * It con
 */
private static void createDef()
{
    CastroIO castroIO = lookup(CastroIO.class);
    if(castroIO.outDir() == null)
        return;
    Path defPath = castroIO.outDir().resolve(castroIO.baseName() + Castro.DEF_EXT);
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
        //compile.registers.dumpAllocation(castroIO, EnumSet.of(BUILTIN));
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
