/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import com.google.common.collect.RangeSet;

import org.antlr.v4.runtime.Token;

import com.raelity.astrolog.castro.Castro.CastroErr;
import com.raelity.astrolog.castro.Castro.CastroLineMaps;
import com.raelity.astrolog.castro.Castro.CastroMapName;
import com.raelity.astrolog.castro.Castro.MacrosAccum;
import com.raelity.astrolog.castro.Castro.RegistersAccum;
import com.raelity.astrolog.castro.Castro.SwitchesAccum;
import com.raelity.astrolog.castro.Constants.Constant;
import com.raelity.astrolog.castro.antlr.AstroParser.ExprContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ExprFuncContext;
import com.raelity.astrolog.castro.antlr.AstroParser.Func_callContext;
import com.raelity.astrolog.castro.antlr.AstroParser.LvalMemContext;
import com.raelity.astrolog.castro.mems.AstroMem;
import com.raelity.astrolog.castro.mems.AstroMem.OutOfMemory;
import com.raelity.astrolog.castro.mems.AstroMem.Var;
import com.raelity.astrolog.castro.mems.AstroMem.Var.VarState;
import com.raelity.astrolog.castro.mems.Macros;
import com.raelity.astrolog.castro.mems.Registers;
import com.raelity.astrolog.castro.mems.Switches;
import com.raelity.astrolog.castro.tables.Functions;
import com.raelity.astrolog.castro.tables.Functions.Function;
import com.raelity.astrolog.castro.tables.Functions.FunctionConstValue;
import com.raelity.astrolog.castro.tables.Functions.StringArgsFunction;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import static com.raelity.astrolog.castro.Castro.MAP_EXT;
import static com.raelity.astrolog.castro.Constants.ConstantFlag.SRC_USER;
import static com.raelity.astrolog.castro.Constants.FK_F0_KEY_CODE;
import static com.raelity.astrolog.castro.Error.*;
import static com.raelity.astrolog.castro.Util.*;
import static com.raelity.astrolog.castro.mems.AstroMem.Var.VarState.*;
import static com.raelity.astrolog.castro.Constants.FK_FIRST_SLOT;
import static com.raelity.astrolog.castro.Constants.FK_LAST_SLOT;
import static com.raelity.astrolog.castro.tables.Functions.NOT_CONST_VALUE;

/**
 * Run the passes. <br>
 * Pass1 - Layout info, var/mem symbol tables, check func name/nargs,
 *         Build LineMap.
 *              TODO: consider: split this pass into two: pass1a and pass1b.
 *              pass1a parse without a visitor, don't continue if error.
 *              The visitor that is now pass1 has lots of code to continue in
 *              the face of error. But NPE might sneak through; so what?
 * Allocation - between pass1 and pass2.
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
private static List<FileData> workingFileData = new ArrayList<>();
private static boolean memoryLocked;
private static boolean parsePass;
private static boolean allocFrozen;

public static boolean isAllocFrozen()
{
    return allocFrozen;
}

/** @return false if there is an error */
static boolean compile(List<String> inputFiles, String outName)
{
    addCastroFunctions();

    // the Accum have info for all the files
    addLookup(new RegistersAccum(new Registers(), new Registers(), new Registers(), new Registers()));
    addLookup(new MacrosAccum(new Macros(), new Macros(), new Macros(), new Macros()));
    addLookup(new SwitchesAccum(new Switches(), new Switches(), new Switches(), new Switches()));

    boolean someError = false;

    //////////////////////////////////////////////////////////////////////
    //
    // First parse the input files, setup file parse IO
    // and record declarations. Create the LineMaps.
    //

    parsePass = true;
    for(String inputFile : inputFiles) {

        CastroIO castroIO = new CastroIO(inputFile, outName, false);
        if(castroIO.getErrorMsg() != null)
            Castro.usage(castroIO.getErrorMsg());
        if(castroIO.isAbort()) {
            someError = true;
            continue;
        }

        CastroMapName castroMap = lookup(CastroMapName.class);
        if(castroMap == null)
            addLookup(new CastroMapName(castroIO.baseName()));

        // Setup things associated with input file for the duration.

        // need castroIO for mem space creation
        replaceLookup(castroIO);
        FileData data = new FileData(castroIO, new Registers(),
                                     new Macros(), new Switches());
        workingFileData.add(data);
        AstroParseResult apr = initLookup(data);
        
        CastroIO.outputFileHeader(castroIO.pw(), ";");

        parseOneFile();
        
        // Copy this pass' variables to defined variables;
        // some may not have addresses. The next file checks its
        // names and addresses against the accumulated defines.
        for(AstroMem mem : lookupAll(AstroMem.class)) {
            mem.addToDefined();
        }
        
        if(apr.hasError())
            someError = true;
    }
    parsePass = false;


    //////////////////////////////////////////////////////////////////////
    //
    // Do the allocations for each file, supplying the defined
    // from all files except the one being allocated.
    //

    //debugDumpAllvars(workingFileData, false);
    boolean oom = false;
    for(FileData data : workingFileData) {
        initLookup(data);

        try {
            applyLayoutsAndAllocate();
        } catch(OutOfMemory ex) {
            Var var = ex.var;
            reportError(ex.var.getId(), "'%s' OutOfMemory size %d, free %s",
                        var.getName(), var.getSize(), ex.free);
            oom = true;
        }
    }

    // Before publishing the globals, output per/file info.
    for(FileData data : workingFileData) {
        initLookup(data);
        createDef();
    }

    if(oom)
        return false;

    // Collect all the variables into "global" container
    for(FileData data : workingFileData) {
        initLookup(data);

        for(AstroMem mem : lookupAll(AstroMem.class)) {
            mem.addToGlobal();
        }
    }

    // Examine each of the global mem spaces accumulated from
    // the compilation units. This acts as an error check
    // since if there were problems, conflicts/overlaps would
    // be detected.

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

    // Publish the global as the mem spaces to use
    // for the rest of the compilation process

    // These remain for the duration
    replaceLookup(globalRegisters);
    replaceLookup(globalMacros);
    replaceLookup(globalSwitches);

    // at least for now, take the map file name from
    // castroIO for the first file.
    if(!createMap())
        someError = true;


    //////////////////////////////////////////////////////////////////////
    //
    // Now that the variables are allocated, proceed with compilation
    //

    allocFrozen = true;

    for(FileData data : workingFileData) {
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

    //debugDumpAllvars(workingFileData, true);

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
}

private static void compileOneFile()
{

    AstroParseResult apr = lookup(AstroParseResult.class);
    PrintWriter err = lookup(CastroErr.class).pw();

    int currentErrorCount = apr.errors();

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
        err.printf("Pass output: %d errors\n", apr.errors() - currentErrorCount);
}

/** Setup the global lookup for the next file that's going to be
 * processed.
 */
private static AstroParseResult initLookup(FileData data)
{
    if(!memoryLocked) {
        replaceLookup(data.registers);
        replaceLookup(data.macros);
        replaceLookup(data.switches);
    }

    if(!parsePass) {
        replaceLookup(lookup(CastroLineMaps.class)
                      .lineMaps().get(data.castroIO.inFile()));
    }

    CastroIO castroIO = data.castroIO;
    replaceLookup(castroIO);
    
    AstroParseResult apr = castroIO.getApr();
    replaceLookup(apr);
    return apr;
    
}

private static void debugDumpAllvars(List<FileData> fData, boolean includeDefined)
{
    for(FileData data : fData) {
        AstroParseResult apr = initLookup(data);

        PrintWriter err = lookup(CastroErr.class).pw();
        err.println();
        EnumSet<VarState> skip = includeDefined
                                 ? EnumSet.of(BUILTIN)
                                 : EnumSet.of(BUILTIN, DEFINED);
        data.registers.dumpVars(err, true, skip);
    }
}

/** The .map file comes from global mem spaces.
 * There are situations where no file is output, that is not an error.
 * @return true if no error
 */
private static boolean createMap()
{
    if(workingFileData.isEmpty())
        return true;
    CastroIO castroIO = workingFileData.get(0).castroIO();
    if(castroIO.inPath() == null)
        return true;
    Path defPath = castroIO.inPath().resolveSibling(lookup(CastroMapName.class).mapName() + MAP_EXT);
    try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(
            defPath, WRITE, TRUNCATE_EXISTING, CREATE))) {
        CastroIO.outputFileHeader(out, "//");
        Registers registers = lookup(RegistersAccum.class).global();
        Macros macros = lookup(MacrosAccum.class).global();
        Switches switches = lookup(SwitchesAccum.class).global();

        registers.dumpVars(out, true, EnumSet.of(BUILTIN), true, false);
        out.println();
        macros.dumpVars(out, true, EnumSet.of(BUILTIN), true, false);
        out.println();
        switches.dumpVars(out, true, EnumSet.of(BUILTIN), true, false);
        out.println();

        for(Constant info : Constants.toList(EnumSet.of(SRC_USER))) {
            String fn = "";
            if(info.token() != null)
                fn = Util.fileName(info.token());
            out.printf("const %s {%s};    // %s:%d\n",
                       info.id(), info.sval(), fn, info.token().getLine());
        }

    } catch(IOException ex) {
        // TODO: FOR NOW count the error in the first file's apr.
        workingFileData.get(0).castroIO.getApr().countError();
        //lookup(AstroParseResult.class).countError();
        lookup(CastroErr.class).pw().printf("Error: '%s' Problem writing %s\n",
                                            ex.getClass().getSimpleName(), defPath);
        return false;
    }
    return true;
}

/** Create and output the .def file containing allocations/definitions.
 * It con
 */
private static void createDef()
{
    CastroIO castroIO = lookup(CastroIO.class);
    if(castroIO.inPath() == null)
        return;
    Path defPath = castroIO.inPath().resolveSibling(castroIO.baseName() + Castro.DEF_EXT);
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
        registers.dumpVars(out, true, EnumSet.of(BUILTIN, DEFINED, EXTERN));
        out.println();
        macros.dumpVars(out, true, EnumSet.of(BUILTIN, DEFINED, EXTERN));
        out.println();
        switches.dumpVars(out, true, EnumSet.of(BUILTIN, DEFINED, EXTERN));
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
                reportError(VAR_RSV, var.getId(),
                       "'%s' assigned to reserve area", var.getName());
        }

        mem.allocate();
    }
}

private static void addCastroFunctions()
{
    Functions.addFunction(new MacroAddress(), "MAddr");
    Functions.addFunction(new SwitchAddress(), "SAddr");
    Functions.addFunction(new KeyCode(), "KeyC");
    Functions.addFunction(new Switch2KeyCode(), "Sw2KC");
    Functions.addFunction(new SizeOf());
}

    /* ************************************************************* */
    private static class KeyCode extends StringArgsFunction
    {
    public KeyCode()
    {
        super("KeyCode", 1);
    }

    @Override
    public StringBuilder genFuncCall(StringBuilder sb, ExprFuncContext ctx,
                                     List<String> args)
    {
        boolean isError = true;
        // There's one arg, should have three characters like "a"
        Token charCodeToken = ctx.fc.strs.get(0);
        String charCodeString = ctx.fc.strs.get(0).getText();
        if(charCodeString.length() < 3)
            reportError(charCodeToken, "empty string");
        else if(charCodeString.length() > 3)
            reportError(charCodeToken, "'%s' only a single character allowed",
                        charCodeString.substring(1, charCodeString.length()-1));
        else {
            char charCode = charCodeString.charAt(1);
            if(charCode < ' ' || charCode > '~')
                reportError(ctx.fc, "'%c' not in range ' ' to '~'", charCode);
            else {
                sb.append((int)charCode).append(' ');
                isError = false;
            }
        }
        if(isError)
            sb.append("#CHARCODE# ");
        return sb;
    }
    
    @Override
    public boolean isDoneReportSpecialFuncArgs(Func_callContext ctx)
    {
        return true;
    }

    } // KeyCode

    /* ************************************************************* */
    private static class Switch2KeyCode extends Function
    {
    public Switch2KeyCode()
    {
        super("Switch2KeyCode", 1);
    }
    
    @Override
    public boolean isDoneReportSpecialFuncArgs(Func_callContext ctx)
    {
        isMacroSwitchFuncArgLval(ctx, targetMemSpace());
        return true;
    }

    @Override public AstroMem targetMemSpace() { return lookup(Switches.class); }

    @Override
    public FunctionConstValue constValue(ExprFuncContext ctx)
    {
        if(!isAllocFrozen())
            return NOT_CONST_VALUE;
        boolean ok = true;
        ExprContext sw = ctx.fc.args.get(0);
        int addr = targetMemSpace().getVar(sw.getText()).getAddr();
        if(addr < FK_FIRST_SLOT || addr > FK_LAST_SLOT)
            ok = false;
        return new FunctionConstValue(ok, FK_F0_KEY_CODE + addr, addr);
    }


    @Override
    public StringBuilder genFuncCall(StringBuilder sb, ExprFuncContext ctx,
                                     List<String> args)
    {
        FunctionConstValue constVal = constValue(ctx);
        if(!constVal.isConst()) {
            ExprContext sw = ctx.fc.args.get(0);
            reportError(sw, "Switch '%s' @%d is not a function key address",
                        sw.getText(), constVal.displayVal());
        }
        sb.append(constVal.realVal()).append(' ');
        return sb;
    }

    } // Switch2KeyCode

    /* ************************************************************* */
    /** Generate the address of given macro. */
    private static class MacroAddress extends SwitchMacroAdress
    {
    private MacroAddress() { super("MacroAddress"); }
    @Override public AstroMem targetMemSpace() { return lookup(Macros.class); }
    }

    /* ************************************************************* */
    /** Generate the address of given switch. */
    private static class SwitchAddress extends SwitchMacroAdress
    {
    private SwitchAddress() { super("SwitchAddress"); }
    @Override public AstroMem targetMemSpace() { return lookup(Switches.class); }
    }

    /* ************************************************************* */
    private static abstract class SwitchMacroAdress extends Function
    {

    public SwitchMacroAdress(String name)
    {
        super(name, 1);
    }

    @Override
    public boolean isDoneReportSpecialFuncArgs(Func_callContext ctx)
    {
        isMacroSwitchFuncArgLval(ctx, targetMemSpace());
        return true; // No further checking required
    }

    @Override
    public FunctionConstValue constValue(ExprFuncContext ctx) {
        List<ExprContext> args = ctx.fc.args;
        // probably don't need size check by the time isAllocFrozen()
        if(!isAllocFrozen() || args.size() != 1)
            return NOT_CONST_VALUE;
        AstroMem memSpace = targetMemSpace();
        LvalMemContext lvalMem = expr2LvalMem(args.get(0));
        if(lvalMem != null) {
            int addr = memSpace.getVar(args.get(0).getText()).getAddr();
            return new FunctionConstValue(true, addr, addr);
        }
        return NOT_CONST_VALUE;
    }

    @Override
    public StringBuilder genFuncCall(StringBuilder sb, ExprFuncContext ctx,
                                     List<String> args)
    {
        if(args.size() == 1)
            sb.append(args.get(0));
        else
            sb.append("#").append(name()).append(":args# ");
        return sb;
    }

    }

    /* ************************************************************* */
    private static class SizeOf extends Function
    {
    public SizeOf()
    {
        super("SizeOf", 1);
    }

    @Override
    public StringBuilder genFuncCall(StringBuilder sb, ExprFuncContext ctx,
                                     List<String> args)
    {
        ExprContext arg = ctx.fc.args.get(0);
        int size = lookup(Registers.class).getVar(arg.getText()).getSize();
        return sb.append(size). append(' ');
    }

    }

}
